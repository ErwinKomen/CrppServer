package nl.ru.crpx.server.requesthandlers;

import java.io.File;
import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.project.CorpusResearchProject;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.util.FileUtil;
import nl.ru.util.json.JSONArray;
import nl.ru.util.json.JSONObject;
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
 *    userid  name under which the data is stored
 *    crp     name of the CRP
 *    start   which hit number to start with
 *    count   the total number of hits to be returned
 *    type    the kind of information needed:
 *            "hits"    - provide list of: file / forestId / hit text
 *            "context" - provide list of: pre / text / post
 *            "syntax"  - provide list of: psd-tree
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

  // =================== Initialisation of this class ==========================
  public RequestHandlerUpdate(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
  }
  
  @Override
  public DataObject handle() {
    String sSub = ""; // Sub category to be returned
    
    try {
      debug(logger, "REQ update");
      // Get the JSON string argument we need to process, e.g:
      //   {  "userid": "erkomen",  // user
      //      "crp": "bladi.crpx",  // name of corpus research project
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
              !jReq.has("count") || !jReq.has("type") || !jReq.has("qc"))
        return DataObject.errorObject("update syntax", 
              "One of the parameters is missing: userid, crp, start, count, type ");
      
      // Now extract the obligatory parameters
      sCurrentUserId = jReq.getString("userid");
      String sCrpName = jReq.getString("crp");
      int iUpdStart = jReq.getInt("start");
      int iUpdCount = jReq.getInt("count");
      String sUpdType = jReq.getString("type");
      int iQC = jReq.getInt("qc");
      
      // Deal with the two optional parameters
      if (jReq.has("sub")) sSub = jReq.getString("sub");
      
      // Get access to the indicated CRP
      CorpusResearchProject crpThis = crpManager.getCrp(sCrpName, sCurrentUserId);
      // Read the table.json file so that we know where what is
      String sTableLoc = crpThis.getDstDir() + "/" + crpThis.getName() + ".table.json";
      JSONArray arTable = new JSONArray(FileUtil.readFile(sTableLoc));
      
      // Get a JSON Array that specifies the position where we can find the data
      JSONArray arHitLocInfo = getHitFileInfo(crpThis, arTable, iQC, sSub, iUpdStart, iUpdCount);

      // Start an array with the required results
      JSONArray arHitDetails = new JSONArray();
      // Start gathering the results
      for (int i=0;i<iUpdCount; i++) {
        // Calculate the number we are looking for
        int iHitNumber = iUpdStart + i;
        JSONObject oHitDetails = new JSONObject();
        oHitDetails.put("n", i);
        // Get the entry from hitlocinfo
        JSONObject oHitLocInfo = arHitLocInfo.getJSONObject(i);
        String sOneSrcFile = oHitLocInfo.getString("file");
        String sLocs = oHitLocInfo.getString("locs");
        String sLocw = oHitLocInfo.getString("locw");
        oHitDetails.put("file", sOneSrcFile);
        oHitDetails.put("locs", sLocs);
        oHitDetails.put("locw", sLocw);
        if (oHitLocInfo.has("msg")) oHitDetails.put("msg", oHitLocInfo.getString("msg"));
        // Construct the target file name
        String sOneSrcFilePart = "";
        
        // Get the information needed for /update
        switch(sUpdType) {
          case "hits":    // Per hit: file // forestId // ru:back() text
            break;
          case "context": // Per hit the contexts: pre // clause // post
            break;
          case "syntax":  // Per hit: file // forestId // node syntax (psd-kind)
            break;
          default:
            break;
        }
        // Add the acquired JSONObject with info about this line
        arHitDetails.put(oHitDetails);
      }
      
      // Load the content for this user
      DataObject objContent = null;
      
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "See the list of CRPs in the [content] section");
      objStatus.put("userid", userId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Providing a CRP list failed", ex, RequestHandlerUpdate.class);
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
          int iQC, String sSub, int iUpdStart, int iUpdCount) {
    JSONArray arBack;     // combines the results
    JSONArray arRes;      // array of [results] within the hit-file/qc combi
    String sHitFile;      // Name of hit-file
    int iResIdx = 0;      // Index into the results array
    int iUpdCurrent;      // Item we are looking for now
    int iUpdFinish;       // Last entry to be taken (starting with 0)
    int iNumber = 0;      // The result number we have found so far
    int iEntryFirst = 0;  // First entry in batch (starting with 0)
    int iEntryLast = 0;   // Laste entry in batch (starting with 0)
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
      int iLastK = 0;
      // Walk the list with hit and subcat counts for this QC
      for (int j=0;j<arQClist.length();j++) {
        // Access this entry as object
        JSONObject oQCentry = (JSONObject) arQClist.get(j);
        // Calculate where we are in terms of hit numbers
        iEntryFirst = iEntryLast;
        if (iSubCat<0)
          iEntryLast = iEntryFirst + oQCentry.getInt("count");
        else
          iEntryLast = iEntryFirst + oQCentry.getJSONArray("subs").getInt(iSubCat);
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
          // (1) Open this file?
          String sLastPart = "/" + sFileName + ".hits";
          if (sHitFile.isEmpty() || !sHitFile.contains(sLastPart)) {
            sHitFile = crpThis.getHitsDir() + sLastPart;
            // Validate existence
            File fThis = new File(sHitFile);
            if (!fThis.exists()) return null;
            // Read the file into a JSON array
            JSONObject oHitF = new JSONObject(FileUtil.readFile(fThis));
            JSONArray arHitF = oHitF.getJSONArray("hits");
            // Get to the results part
            arRes = ((JSONObject) arHitF.get(iQC-1)).getJSONArray("results");
            // Initialize the result index
            iResIdx = 0;
            // Reset the 'lastk' variable
            iLastK = 0;
          }
          // (2) validate
          if (arRes == null) return null;
          // (3) Move the result-index to the required iOffset
          if (sSub.isEmpty()) {
            // The result index is straight-forward the index of the [results] array
            JSONObject oOneRes = arRes.getJSONObject(iOffset);
            oAdd.put("locs", oOneRes.getString("locs"));
            oAdd.put("locw", oOneRes.getString("locw"));
            if (oOneRes.has("msg"))
              oAdd.put("msg", oOneRes.getString("msg"));
          } else {
            // Find the 'iOffset's entry from subcategory 'sSub'
            for (int k=iLastK; k < arRes.length(); k++) {
              // Keep track of the last offset
              iLastK = k+1;
              // Get this entry
              JSONObject oOneRes = arRes.getJSONObject(k);
              if (oOneRes.getString("cat").equals(sSub)) {
                iResIdx++;
                if (iResIdx == iOffset) {
                  // Process this one
                  oAdd.put("locs", oOneRes.getString("locs"));
                  oAdd.put("locw", oOneRes.getString("locw"));
                  if (oOneRes.has("msg"))
                    oAdd.put("msg", oOneRes.getString("msg"));                  
                  break;
                }
              }
            }
          }
          
          // Add this entry to the results
          arBack.put(oAdd);
          // Continue until we have received all that is needed
          iUpdCurrent++;
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
