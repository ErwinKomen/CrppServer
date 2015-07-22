package nl.ru.crpx.server.requesthandlers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.servlet.http.HttpServletRequest;
import javax.xml.xpath.XPathExpressionException;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectList;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.project.CorpusResearchProject;
import nl.ru.crpx.project.CorpusResearchProject.ProjType;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.crpx.xq.English;
import nl.ru.crpx.xq.RuBase;
import nl.ru.util.FileUtil;
import nl.ru.util.json.JSONArray;
import nl.ru.util.json.JSONObject;
import nl.ru.xmltools.XmlAccess;
import nl.ru.xmltools.XmlAccessPsdx;
import nl.ru.xmltools.XmlDocument;
import nl.ru.xmltools.XmlIndexReader;
import nl.ru.xmltools.XmlNode;
import org.apache.log4j.Logger;

/*
 * This software has been developed at the "Meertens Instituut"
 *   for the CLARIN project "CorpusStudio-WebApplication".
 * The application is based on the "CorpusStudio" program written by Erwin R. Komen
 *   while working for the Radboud University Nijmegen.
 * The program and the source can be freely used and re-distributed.
 */


/**
 * RequestHandlerUpdate
 *    Provide detailed information about a search job that has (already)
 *    been executed. The kind of information to be provided depends on the 
 *    parameters passed on here:
 * 
 *      userid  name under which the data is stored
 *      crp     name of the CRP
 *      lng     the language on which the CRP has been run
 *      dir     the part of the language (corpus specification)
 *      start   which hit number to start with
 *      count   the total number of hits to be returned
 *      type    the kind of information needed:
 *              "hits"    - provide list of: file / forestId / hit text
 *              "context" - provide list of: pre / text / post
 *              "syntax"  - provide list of: psd-tree
 * 
 * @author  Erwin R. Komen
 * @history 16/jul/2015 created
 */
public class RequestHandlerUpdate extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerUpdate.class);
  // =================== Local variables =======================================
  private CrpManager crpManager;
  private Processor objSaxon;               // Local access to the processor
  private DocumentBuilder objSaxDoc;        // My own document-builder
  private XmlIndexReader objXmlRdr=null;    // Index reader for current file
  private File objCurrentFile = null;       // File we are working on now
  private String loc_xpWords = "";          // Xpath expression to get to the words
  private ProjType iPrjType;                // Type of current project (psdx/folia...)
  private static final QName loc_attr_LeafText = new QName("", "", "Text");

  // =================== Initialisation of this class ==========================
  public RequestHandlerUpdate(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
  }
  
  @Override
  public DataObject handle() {
    String sSub = "";         // Sub category to be returned
    String sLngPart = "";     // Part of the corpus to be accessed
    JSONArray arFiles = null; // List of file names separated by tabs
    XmlAccess objXmlAcc;      // XML access to the file(chunk) we are working with
    
    try {
      debug(logger, "REQ update");
      // Get the JSON string argument we need to process, e.g:
      //   {  "userid": "erkomen",  // user
      //      "crp": "bladi.crpx",  // name of corpus research project
      //      "lng": "eng_hist",    // language from which data is processed
      //      "dir": "ME",          // part of the language corpus being accessed
      //      "start": 20,          // index of first hit
      //      "count": 50,          // number of hits (within the category)
      //      "qc": 3,              // QC line number
      //      "sub": "3[sv]",       // OPTIONAL: Sub category
      //      "type": "syntax"      // Action required: "hits", "context" or "syntax"
      //   }
      // Note: if no user is given, then we should give all users and all crp's
      sReqArgument = getReqString(request);
      logger.debug("Considering request /update: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      // Validate obligatory parameters
      if (!jReq.has("userid") || !jReq.has("crp") || !jReq.has("start") || 
              !jReq.has("count") || !jReq.has("type") || !jReq.has("qc") ||
              !jReq.has("lng"))
        return DataObject.errorObject("update syntax", 
              "One of the parameters is missing: userid, crp, start, count, type, qc, lng ");
      
      // Now extract the obligatory parameters
      sCurrentUserId = jReq.getString("userid");
      String sCrpName = jReq.getString("crp");
      String sLngName = jReq.getString("lng");
      int iUpdStart = jReq.getInt("start");
      int iUpdCount = jReq.getInt("count");
      String sUpdType = jReq.getString("type");
      int iQC = jReq.getInt("qc");
      
      // Deal with the optional parameter(s)
      if (jReq.has("sub")) sSub = jReq.getString("sub");
      if (jReq.has("dir")) sLngPart = jReq.getString("dir");
      if (jReq.has("files")) arFiles = jReq.getJSONArray("files");
      
      // Get access to the indicated CRP
      CorpusResearchProject crpThis = crpManager.getCrp(sCrpName, sCurrentUserId);
      
      // Validate
      if (crpThis == null)
        return DataObject.errorObject("availability", 
              "The CRP with the indicated name is not available for this user");
      
      // Keep the project type
      this.iPrjType = crpThis.intProjType;
      
      // Get Access to Saxon
      this.objSaxon = crpThis.getSaxProc();
      this.objSaxDoc = this.objSaxon.newDocumentBuilder();
      // Initialize access to ANY document associated with this CRP
      XmlDocument pdxThis = new XmlDocument(this.objSaxDoc, this.objSaxon);
      
      // Read the table.json file so that we know where what is
      String sTableLoc = crpThis.getDstDir() + "/" + crpThis.getName() + ".table.json";
      // Validate
      File fTableLoc = new File(sTableLoc);
      if (!fTableLoc.exists())
        return DataObject.errorObject("availability", 
              "The CRP with the indicated name has not yet been run. Use /exe to run it first.");
      // Load the table
      JSONArray arTable = new JSONArray(FileUtil.readFile(fTableLoc));
      
      // Get a JSON Array that specifies the position where we can find the data
      JSONArray arHitLocInfo = getHitFileInfo(crpThis, arTable, iQC, sSub, arFiles, iUpdStart, iUpdCount);

      // Get the directory where corpus files must be found
      String sCrpLngDir = servlet.getSearchManager().getCorpusPartDir(sLngName, sLngPart);
      
      // Start an array with the required results
      // JSONArray arHitDetails = new JSONArray();
      DataObjectList arHitDetails = new DataObjectList("content");
      String sLastFile = ""; String sOneSrcFilePart = "";
      objXmlAcc = null;
      // Start gathering the results
      for (int i=0;i<iUpdCount && i < arHitLocInfo.length(); i++) {
        // Calculate the number we are looking for
        int iHitNumber = iUpdStart + i;
        // Start storing the details of this hit
        DataObjectMapElement oHitDetails = new DataObjectMapElement();
        //JSONObject oHitDetails = new JSONObject();
        oHitDetails.put("n", iHitNumber);
        // Get the entry from hitlocinfo
        JSONObject oHitLocInfo = arHitLocInfo.getJSONObject(i);
        String sOneSrcFile = oHitLocInfo.getString("file");
        String sLocs = oHitLocInfo.getString("locs");
        String sLocw = oHitLocInfo.getString("locw");
        oHitDetails.put("file", sOneSrcFile);
        oHitDetails.put("locs", sLocs);
        oHitDetails.put("locw", sLocw);
        if (oHitLocInfo.has("msg")) oHitDetails.put("msg", oHitLocInfo.getString("msg"));
        // Do we have this file already?
        if (sLastFile.isEmpty() || !sLastFile.equals(sOneSrcFile)) {
          // Construct the target file name
          sOneSrcFilePart = FileUtil.findFileInDirectory(sCrpLngDir, sOneSrcFile);
          sLastFile = sOneSrcFile;
          // Create an Xml accesser for this particular type
          switch (crpThis.intProjType) {
            case ProjPsdx:
             objXmlAcc = new XmlAccessPsdx(crpThis, pdxThis, sOneSrcFilePart);
            case ProjAlp:
            case ProjNegra:
            case ProjFolia:
            default:
              break;
          }
        }
        // Validate
        if (objXmlAcc == null)
          return DataObject.errorObject("incompatibility", 
              "The interface to the XML files of type ["+crpThis.getProjectType()+"] is not yet implemented");
        // Get access to the XML sentence belonging to this @locs
        // objXmlAc = getOneSentence(crpThis, pdxThis, sOneSrcFilePart, sLocs);
        
        // Convert [sUpdType] into an array
        String[] arUpdType = {sUpdType};
        if (sUpdType.contains("\\+"))
          arUpdType = sUpdType.split("[+]");
        else if (sUpdType.contains(" "))
          arUpdType = sUpdType.split(" ");
        else if (sUpdType.contains("\\|"))
          arUpdType = sUpdType.split("[|]");
        else if (sUpdType.contains("_"))
          arUpdType = sUpdType.split("_");
        
        // Get the information needed for /update
        JSONObject oHitInfo = null;
        for (int k=0;k<arUpdType.length;k++) {
          switch(arUpdType[k].trim()) {
            case "hits":    // Per hit: file // forestId // ru:back() text
              oHitInfo = objXmlAcc.getHitLine(sLngName, sLocs, sLocw);
              oHitDetails.put("preH", oHitInfo.getString("pre"));
              oHitDetails.put("hitH", oHitInfo.getString("hit"));
              oHitDetails.put("folH", oHitInfo.getString("fol"));
              break;
            case "context": // Per hit the contexts: pre // clause // post
              oHitInfo = objXmlAcc.getHitContext(sLngName, sLocs, sLocw, 
                      crpThis.getPrecNum(), crpThis.getFollNum());
              oHitDetails.put("preC", oHitInfo.getString("pre"));
              oHitDetails.put("hitC", oHitInfo.getString("hit"));
              oHitDetails.put("folC", oHitInfo.getString("fol"));
              break;
            case "syntax":  // Per hit: file // forestId // node syntax (psd-kind)
              DataObjectMapElement oHitSyntax = (DataObjectMapElement) objXmlAcc.getHitSyntax(sLngName, sLocs, sLocw);
              oHitDetails.put("allS", oHitSyntax.get("all"));
              oHitDetails.put("hitS", oHitSyntax.get("hit"));
              break;
            default:
              break;
          }
        }

        // Add the acquired JSONObject with info about this line
        arHitDetails.add(oHitDetails);
      }
      
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "See the information in the [content] section");
      objStatus.put("userid", userId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", arHitDetails);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Providing /update information failed", ex, RequestHandlerUpdate.class);
      return null;
    }
  }
  
  /**
   * getHitFileInfo
   *    Get location information as specified by the parameters
   * 
   * @param arTable
   * @param iQC
   * @param sSub
   * @param iUpdStart
   * @param iUpdCount
   * @return 
   */
  private JSONArray getHitFileInfo(CorpusResearchProject crpThis, JSONArray arTable, 
          int iQC, String sSub, JSONArray arFiles, int iUpdStart, int iUpdCount) {
    JSONArray arBack;     // combines the results
    JSONArray arRes;      // array of [results] within the hit-file/qc combi
    String sHitFile;      // Name of hit-file
    int iUpdCurrent;      // Item we are looking for now
    int iUpdFinish;       // Last entry to be taken (starting with 0)
    int iEntryFirst = -1; // Total result count number: first entry in batch (starting with 0)
    int iEntryLast = -1;  // Total result count number: Last entry in batch (starting with 0)
    int iSubCat = -1;     // Index of the subcat (if specified)
    
    try {
      // Initializations
      arBack = new JSONArray();
      arRes = null;
      iUpdCurrent = iUpdStart -1;
      iUpdFinish = iUpdCurrent + iUpdCount -1;
      sHitFile = "";
      // Find the QC that is needed
      if (arTable.length()<iQC-1) return null;
      JSONObject oQClist = arTable.getJSONObject(iQC-1);
      // Do we need to have a sub-category number?
      if (!sSub.isEmpty()) {
        // Get the list of subcategories
        JSONArray arSubCats = oQClist.getJSONArray("subcats");
        for (int i=0;i<arSubCats.length(); i++ ) {
          if (arSubCats.getString(i).equals(sSub)) {
            iSubCat = i; break;
          }
        }
        // Validate
        if (iSubCat<0) return null;
      }
      
      // Get to the hits for this QC
      if (!oQClist.has("hits")) return null;
      JSONArray arQClist = oQClist.getJSONArray("hits");
      
      // If there are any files in [arFiles], then remove those that are not
      //   in the subscription out of the [hits]
      if (arFiles != null && arFiles.length() > 0) {
        // Convert the list to a searchable one
        List<String> lstHits = new ArrayList<>();
        for (int j=0;j<arFiles.length();j++) {lstHits.add(arFiles.getString(j));}
        // Check the list of hits
        for (int j=arQClist.length()-1;j>=0;j--) {
          // Access this one
          JSONObject oOne = (JSONObject)arQClist.get(j);
          // Check if this one should be included
          if (!lstHits.contains(oOne.getString("file"))) {
            // Remove it from the list
            arQClist.remove(j);
          }
        }
      }
      
      int iLastK = 0;
      // Walk the list with hit and subcat counts for this QC
      for (int j=0;j<arQClist.length();j++) {
        // Access this entry as object
        JSONObject oQCentry = (JSONObject) arQClist.get(j);
        // Calculate where we are in terms of hit numbers
        if (iSubCat<0) {
          if (oQCentry.getInt("count")>0) {
            iEntryFirst = iEntryLast + 1;
            iEntryLast = iEntryFirst + oQCentry.getInt("count")-1;
          }
        } else {
          int iSubCatCount = oQCentry.getJSONArray("subs").getInt(iSubCat);
          if (iSubCatCount>0) {
            iEntryFirst = iEntryLast + 1;
            iEntryLast = iEntryFirst + oQCentry.getJSONArray("subs").getInt(iSubCat)-1;
          }
        }
        // should we process this entry?
        while (iUpdCurrent >= iEntryFirst && iUpdCurrent <= iEntryLast && 
                iUpdCurrent <= iUpdFinish) {
          // Get file name and offset within the file 
          String sFileName = oQCentry.getString("file");
          int iOffset = iUpdCurrent - iEntryFirst;
          // Get the required information
          JSONObject oAdd = new JSONObject();
          oAdd.put("file", sFileName);
          // oAdd.put("offset", iOffset);
          oAdd.put("qc", iQC);
          oAdd.put("sub", sSub);
          // Get the 'locs', 'locw', 'cat' and 'msg' for this entry
          // (1) Open this file in order to get information from it?
          String sLastPart = "/" + sFileName + ".hits";
          if (sHitFile.isEmpty() || !sHitFile.contains(sLastPart)) {
            sHitFile = crpThis.getHitsDir() + sLastPart;
            // Validate existence
            File fThis = new File(sHitFile);
            if (!fThis.exists()) return null;
            // Read the file into a JSON array
            JSONObject oHitF = new JSONObject(FileUtil.readFile(fThis));
            JSONArray arHitF = oHitF.getJSONArray("hits");
            // Get to the results part of this QC combined with possible subcat
            if (sSub.isEmpty()) {
              // It is sufficient to get the "results" array from the indicated QC
              arRes = ((JSONObject) arHitF.get(iQC-1)).getJSONArray("results");
            } else {
              // First get the "percat" array of the indicated QC
              JSONArray arPerCat = ((JSONObject) arHitF.get(iQC-1)).getJSONArray("percat");
              arRes = null;
              // Walk this array looking for the correct subcat
              for (int k=0;k<arPerCat.length();k++) {
                // Access this 'percat' element
                JSONObject oPerCat = arPerCat.getJSONObject(k);
                // Does this 'percat' element involve our requested sub-category?
                if (oPerCat.getString("cat").equals(sSub)) {
                  // Found it!
                  arRes = oPerCat.getJSONArray("results");
                  // get out of the loop
                  break;
                }
              }
            }
            // Initialize the result index
            // iResIdx = 0;
            // Reset the 'lastk' variable
            iLastK = 0;
          }
          // (2) validate: we can only continue if there are any results in this file
          if (arRes != null && arRes.length()>0) {
            // (3) Move the result-index to the required iOffset
            // The result index is straight-forward the index of the [results] array
            JSONObject oOneRes = arRes.getJSONObject(iOffset);
            oAdd.put("locs", oOneRes.getString("locs"));
            oAdd.put("locw", oOneRes.getString("locw"));
            if (oOneRes.has("msg"))
              oAdd.put("msg", oOneRes.getString("msg"));
            // Add this entry to the results
            arBack.put(oAdd);
            // Continue until we have received all that is needed
            iUpdCurrent++;
          }

        }
        // Check: if we are ready, we can leave
        if (iUpdCurrent > iUpdFinish) break;
      }
      
      // Return the result
      return arBack;
    } catch (Exception ex) {
      errHandle.DoError("getHitFileInfo failed", ex, RequestHandlerUpdate.class);
      return null;
    }
  }
  
}
