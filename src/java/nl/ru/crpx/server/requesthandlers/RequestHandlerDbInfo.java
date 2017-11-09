/**
 * Copyright (c) 2015 CLARIN-NL.
 * All rights reserved.
 *
 * This software has been developed at the "Meertens Instituut"
 *   for the CLARIN project "CorpusStudio-WebApplication".
 * The application is based on the "CorpusStudio" program written by Erwin R. Komen
 *   while working for the Radboud University Nijmegen.
 * The program and the source can be freely used and re-distributed.
 * 
 * @author Erwin R. Komen
 */
package nl.ru.crpx.server.requesthandlers;

import java.io.File;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectList;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.project.CorpusResearchProject;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.crpx.xq.Extensions;
import nl.ru.util.ByRef;
import nl.ru.util.FileUtil;
import nl.ru.util.json.JSONArray;
import nl.ru.util.json.JSONObject;
import nl.ru.xmltools.XmlAccess;
import nl.ru.xmltools.XmlAccessFolia;
import nl.ru.xmltools.XmlAccessPsdx;
import nl.ru.xmltools.XmlDocument;
import nl.ru.xmltools.XmlIndexTgReader;
import nl.ru.xmltools.XmlNode;
import nl.ru.xmltools.XmlResultDbase;
import org.apache.log4j.Logger;

/**
 * RequestHandlerDbInfo
 *    Provide detailed information about an available result database. 
 *    The kind of information to be provided depends on the 
 *    parameters passed on here:
 * 
 *      userid  name under which the data is stored
 *      name    name of the database
 *      start   which hit number to start with - if negative: give general information
 *      count   the total number of hits to be returned
 * 
 * @author  Erwin R. Komen
 * @history 28/may/2016 created
 */
public class RequestHandlerDbInfo extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerDbInfo.class);
  // =================== Local variables =======================================
  private CrpManager crpManager;
  private Processor objSaxon;               // Local access to the processor
  private DocumentBuilder objSaxDoc;        // My own document-builder
  private XmlIndexTgReader objXmlRdr=null;    // Index reader for current file
  private XmlDocument pdxThis = null;
  private XmlAccess objXmlAcc;              // XML access to the file(chunk) we are working with
  private File objCurrentFile = null;       // File we are working on now
  private Extensions ruExt = null;          // To make sure Extension functions work
  private String loc_xpWords = "";          // Xpath expression to get to the words
  private String sCrpLngDir = "";
  private String sLngName = "";
  private String sLngPart = "";
  private String sCurrentFile = "";         // Current file we are working on
  private long startTime = 0;
  private long tmeMeta = 0;                 // Time to do meta information
  private long tmeKwic = 0;                 // Time to get KWIC information
  private JSONObject oCurrentMetaInfo = null; // Metadata of the current file
  private CorpusResearchProject.ProjType iPrjType;  // Type of current project (psdx/folia...)
  private CorpusResearchProject crpThis = null;
  // =================== Final Locals ==========================================
  private static final QName loc_attr_LeafText = new QName("", "", "Text");
  private final String sKwicMethod = "calculate";
  private final String sMetaMethod = "calculate";

  // =================== Initialisation of this class ==========================
  public RequestHandlerDbInfo(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
  }
  
  @Override
  public DataObject handle() {
    
    try {
      debug(logger, "REQ dbinfo");
      // Get the JSON string argument we need to process, e.g:
      //   {  "userid": "erkomen",  // user
      //      "name": "bladi.xml",  // name of database
      //      "start": 20,          // index of first hit
      //      "filter": {           // List of STRING filter expressions
      //         "Title": "ab*",
      //         "Genre": "c*" },
      //      "count": 50,          // number of hits (within the category)
      //      "sort": "-Cat",       // Column name that needs sorting + minus sign if descending order
      //   }
      // Note: if no user is given, then we should give all users and all crp's
      sReqArgument = getReqString(request);
      logger.debug("Considering request /dbinfo: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      // Validate obligatory parameters
      if (!jReq.has("userid") || !jReq.has("name") || !jReq.has("start") || 
              !jReq.has("count") )
        return DataObject.errorObject("dbinfo syntax", 
              "One of the parameters is missing: userid, name, start, count ");
      
      // Now extract the obligatory parameters
      sCurrentUserId = jReq.getString("userid");
      String sDbName = jReq.getString("name");
      int iUpdStart = jReq.getInt("start");
      int iUpdCount = jReq.getInt("count");
      
      // Deal with optional parameters
      String sSort = "";
      if (jReq.has("sort")) { sSort = jReq.getString("sort"); }
      JSONObject oFilter = null;
      if (jReq.has("filter")) { oFilter = jReq.getJSONObject("filter"); }
      
      // Gain access to the database through a reader
      CorpusResearchProject oCrpx = new CorpusResearchProject(true);
      XmlResultDbase oDbIndex = new XmlResultDbase(oCrpx, null, errHandle);
      // XmlResultPsdxIndex oDbIndex = new XmlResultPsdxIndex(oCrpx, null, errHandle);
      String sDbFile = "/etc/project/" + sCurrentUserId + "/dbase/" + sDbName;
      if (!oDbIndex.Prepare(sDbFile)) return DataObject.errorObject("availability", 
              "The database with the indicated name cannot be loaded for this user");
      
      // Possibly perform sorting
      oDbIndex.Sort(sSort);
      
      // Possibly perform filtering
      oDbIndex.Filter(oFilter);
      
      // Start a content object
      DataObjectMapElement objContent = new DataObjectMapElement();
      
      // The general information of the database must be added at any rate (no matter the value of iUpdStart)
      DataObjectMapElement objGeneral = (DataObjectMapElement) getGeneralPart(oDbIndex);
      objContent.put("General", objGeneral);
      
      // Prepare getting KIWC results
      this.kwicPrepare(oDbIndex);
      
      // Start an array with the required results
      DataObjectList arHitDetails = new DataObjectList("results");
      int iCount = 0;     // Number of results actually given
      
      // Find out what the operation is: if start less than zero, then provide the general info
      if (iUpdStart >=0 && iUpdCount>0) {
        // Call the routine to get a number of results
        ByRef<JSONArray> arResults = new ByRef(null);
        arResults.argValue = new JSONArray();
        if (!oDbIndex.getResults(arResults, iUpdStart, iUpdCount)) return DataObject.errorObject("runtime", 
                "Could not retrieve the requested results");
        // Copy the results to the dataobject map element
        for (int i=0;i<arResults.argValue.length();i++) {
          // Get this one result
          JSONObject oResSource = arResults.argValue.getJSONObject(i);
          DataObjectMapElement oResTarget = new DataObjectMapElement();
          // Extract essential parameters
          String sFile = oResSource.getString("File");
          String sLocs = oResSource.getString("Locs");
          String sLocw = oResSource.getString("Locw");
          String sSubTypeResult = oResSource.getString("SubType");
          // COpy the 'standard' ones to the target
          oResTarget.put("File", sFile);
          oResTarget.put("Locs", sLocs);
          oResTarget.put("Locw", sLocw);
          oResTarget.put("ResId", oResSource.getInt("ResId"));
          oResTarget.put("TextId", oResSource.getString("TextId"));
          oResTarget.put("Cat", oResSource.getString("Cat"));
          oResTarget.put("SubType", sSubTypeResult);
          // Add metadata
          switch (sMetaMethod) {
            case "internal":
              oResTarget.put("Title", oResSource.getString("Title"));
              oResTarget.put("Genre", oResSource.getString("Genre"));
              oResTarget.put("Author", oResSource.getString("Author"));
              oResTarget.put("Date", oResSource.getString("Date"));          
              break;
            case "calculate":
              startTime = System.nanoTime();
              // Calculate the meta information for this hit
              JSONObject oMetaInfo = getResultMeta(sLngName, sLngPart, sFile);
              this.tmeMeta += System.nanoTime() - startTime;
              if (oMetaInfo == null) {
                // Add the meta information to the result
                oResTarget.put("Title", "");
                oResTarget.put("Genre", "");
                oResTarget.put("Author", "");
                oResTarget.put("Date", "");          
                oResTarget.put("Size", 0);          
              } else {
                // Add the meta information to the result
                oResTarget.put("Title", oMetaInfo.getString("Title"));
                oResTarget.put("Genre", oMetaInfo.getString("Genre"));
                oResTarget.put("Author", oMetaInfo.getString("Author"));
                oResTarget.put("Date", oMetaInfo.getString("Date"));
                oResTarget.put("Size", oMetaInfo.getInt("Size"));
                // Possibly add better subtype
                String sSubTypeMeta = oMetaInfo.getString("SubType");
                if (sSubTypeResult.isEmpty() && !sSubTypeMeta.isEmpty()) {
                  oResTarget.put("SubType", sSubTypeMeta);
                }
              }
              break;
          }
          // Add sentence-context information
          switch (sKwicMethod) {
            case "calculate":
              startTime = System.nanoTime();
              // Calculate and copy the Kwic for this hit
              JSONObject oKwicInfo = getResultKwic(sFile, sLocs, sLocw);
              this.tmeKwic += System.nanoTime() - startTime;
              // Add the KWIC info to the result
              oResTarget.put("kwic_pre", oKwicInfo.getString("pre"));
              oResTarget.put("kwic_hit", oKwicInfo.getString("hit"));
              oResTarget.put("kwic_fol", oKwicInfo.getString("fol"));
              break;
            case "internal":
              // Add the KWIC info to the result
              oResTarget.put("kwic_pre", oResSource.getString("Pre"));
              oResTarget.put("kwic_hit", oResSource.getString("Hit"));
              oResTarget.put("kwic_fol", oResSource.getString("Fol"));
              break;
          }
          // COpy the features to the target
          DataObjectList arFeatDst = new DataObjectList("features");
          JSONArray arFeatSrc = oResSource.getJSONArray("Features");
          for (int k=0;k<arFeatSrc.length();k++) {
            JSONObject oFeatSrc = arFeatSrc.getJSONObject(k);
            DataObjectMapElement oFeatDst = new DataObjectMapElement();
            oFeatDst.put(oFeatSrc.getString("Name"), oFeatSrc.getString("Value"));
            arFeatDst.add(oFeatDst);
          }
          oResTarget.put("Features", arFeatDst);
          
          // Add to the array of hits
          arHitDetails.add(oResTarget); 
          // Keep track of the actual count
          iCount +=1;
        }
      }
      
      // Add the number of results actually given
      objContent.put("Count", iCount);
      // Add the total number of results
      objContent.put("Size", oDbIndex.Size());
      // Add the array of results
      objContent.put("Results", arHitDetails);
      // Add the array of feature names
      DataObjectList arFtNames = new DataObjectList("features");
      for (String sFtName : oDbIndex.featureList()) {arFtNames.add(sFtName);}
      objContent.put("Features", arFtNames);
      
      // ============= TIMING
      objContent.put("TimeMeta", this.tmeMeta);
      objContent.put("TimeKwic", this.tmeKwic);
      
      // Make sure the database connection is closed again
      oDbIndex.close();
 
      
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "See the information in the [content] section");
      objStatus.put("userid", userId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Providing /dbinfo information failed", ex, RequestHandlerDbInfo.class);
      return null;
    }
  }
  
  /**
   * getGeneralPart --
   *    Retrieve information from the <General> part of the results database
   * 
   * @param oDbIndex
   * @return 
   */
  private DataObject getGeneralPart(XmlResultDbase oDbIndex) {
    try {
      // THe user wants to have all the information in the <General> section
      JSONObject oHdr = oDbIndex.headerInfo();
      // Make sure certain parts are copied
      String sProjectName = ""; // Name of CRPX that created the DB
      String sCreated = "";     // Created date in sortable date/time
      String sLanguage = "";    // Main language
      String sPart = "";        // Part of corpus
      String sNotes = "";       // Notes to the DB
      String sAnalysis = "";    // Names of all features used
      int iQC = 1;              // The QC number for this database
      if (oHdr.has("ProjectName")) sProjectName = oHdr.getString("ProjectName");
      if (oHdr.has("Created")) sCreated = oHdr.getString("Created");
      if (oHdr.has("Language")) sLanguage = oHdr.getString("Language");
      if (oHdr.has("Part")) sPart = oHdr.getString("Part");
      if (oHdr.has("Notes")) sNotes = oHdr.getString("Notes");
      if (oHdr.has("Analysis")) sAnalysis = oHdr.getString("Analysis");
      if (oHdr.has("QC")) iQC = oHdr.getInt("QC");
      // Put all into a datamapelement
      DataObjectMapElement oGeneral = new DataObjectMapElement();
      oGeneral.put("ProjectName", sProjectName);
      oGeneral.put("Created", sCreated);
      oGeneral.put("Language", sLanguage);
      oGeneral.put("Part", sPart);
      oGeneral.put("Notes", sNotes);
      oGeneral.put("Analysis", sAnalysis);
      oGeneral.put("QC", iQC);
      // Also add a list of features
      List<String> lFeatures = oDbIndex.featureList();
      DataObjectList lFtList = new DataObjectList("ftlist");
      for (int i=0;i<lFeatures.size(); i++) { lFtList.add(lFeatures.get(i)); }
      oGeneral.put("Features", lFtList);
      // Return the result
      return oGeneral;
    } catch (Exception ex) {
      errHandle.DoError("RequestHandlerDbInfo/getGeneralPart", ex, RequestHandlerDbInfo.class);
      return null;
    }
  }
  
  /**
   * kwicPrepare
   *    Prepare getting Kwic results
   * 
   * @param oDbIndex
   * @return 
   */
  public boolean kwicPrepare(XmlResultDbase oDbIndex) {
    JSONObject oHdr = null;
    
    try {
      // Get the header
      oHdr = oDbIndex.headerInfo();
      // Get the directory where corpus files must be found
      this.sLngPart = oHdr.getString("Part");
      this.sLngName = oHdr.getString("Language");
      this.sCrpLngDir = servlet.getSearchManager().getCorpusPartDir(sLngName, sLngPart);
      
      // Get access to the indicated CRP
      String sCrpName = oHdr.getString("ProjectName");
      this.crpThis = crpManager.getCrp(sCrpName, sCurrentUserId);
      
      // Validate
      if (this.crpThis == null) return false;
      
      // Keep the project type
      this.iPrjType = crpThis.intProjType;
      
      // Get Access to Saxon
      this.objSaxon = crpThis.getSaxProc();
      this.objSaxDoc = this.objSaxon.newDocumentBuilder();
      // Initialize access to ANY document associated with this CRP
      this.pdxThis = new XmlDocument(this.objSaxDoc, this.objSaxon);
      
      
      // Return success
      return true;
    } catch (Exception ex) {
      errHandle.DoError("RequestHandlerDbInfo/kwicPrepare", ex, RequestHandlerDbInfo.class);
      return false;
    }
  }
  
  /**
   * getTextAccess
   *    Retrive an XmlAccess handle to the indicated text file
   * 
   * @param sFile
   * @return 
   */
  private XmlAccess getTextAccess(String sFile) {
    try {
      if (!this.sCurrentFile.equals(sFile)) {
        this.sCurrentFile = sFile;
        
        // Construct the target file name
        String sOneSrcFilePart = FileUtil.findFileInDirectory(sCrpLngDir, sFile);

        // Get a handle to this file
        switch (crpThis.intProjType) {
          case ProjPsdx:
           objXmlAcc = new XmlAccessPsdx(crpThis, pdxThis, sOneSrcFilePart); break;
          case ProjFolia:
           objXmlAcc = new XmlAccessFolia(crpThis, pdxThis, sOneSrcFilePart); break;              
          case ProjAlp:
            break;
          case ProjNegra:
            break;
          default:
            break;
        }
      }
      
      // Return the object we created
      return objXmlAcc;
    } catch (Exception ex) {
      errHandle.DoError("RequestHandlerDbInfo/getTextAccess", ex, RequestHandlerDbInfo.class);
      return null;
    }
  }
  
  /**
   * getResultMeta
   *    Retrieve the metadata for this file
   * 
   * @param sLng
   * @param sPart
   * @param sFile
   * @return 
   */
  private JSONObject getResultMeta(String sLng, String sPart, String sFile) {
    JSONObject oMetaInfo;
    JSONObject oBack = new JSONObject();
    
    try {
      // Has the file changed?
      if (this.sCurrentFile.equals(sFile)) {
        oMetaInfo = this.oCurrentMetaInfo;
      } else {
        // Get the full path of the filename
        String sOneSrcFilePart = FileUtil.findFileInDirectory(sCrpLngDir, sFile);
        // Retrieve the metadata 
        oMetaInfo = this.crpManager.getMetaInfo(sLng, sPart, sOneSrcFilePart);
        this.oCurrentMetaInfo = oMetaInfo;
      }
      if (oMetaInfo == null) {
        // This text is not available
        errHandle.DoError("getResultMeta: cannot find text: " + sFile);
        return null;
      }
      oBack.put("Title", oMetaInfo.getString("title"));
      oBack.put("Genre", oMetaInfo.getString("genre"));
      oBack.put("Author", oMetaInfo.getString("author"));            
      oBack.put("Date", oMetaInfo.getString("date"));            
      oBack.put("SubType", oMetaInfo.getString("subtype"));            
      oBack.put("Size", oMetaInfo.getInt("size"));            
      
      // Return the object we created
      return oBack;
    } catch (Exception ex) {
      errHandle.DoError("RequestHandlerDbInfo/getResultMeta", ex, RequestHandlerDbInfo.class);
      return null;
    }
  }
  /**
   * getResultKwic
   *    Get KWIC information around file/locs/locw
   * 
   * @param sFile       - Name of this file
   * @param sLocs       - Sentence identifier
   * @param sLocw       - Identifier of constituent within sentence
   * @return 
   */
  public JSONObject getResultKwic(String sFile, String sLocs, String sLocw) {
    try {
      // Get to the file
      objXmlAcc = this.getTextAccess(sFile);

      // Validate
      if (objXmlAcc == null) return null;
      // Per hit the contexts: pre // clause // post
      return objXmlAcc.getHitLine(sLngName, sLocs, sLocw);
    } catch (Exception ex) {
      errHandle.DoError("RequestHandlerDbInfo/getResultKwic", ex, RequestHandlerDbInfo.class);
      return null;
    }
  }
  
  /**
   * getOneResult --
   *    Retrieve the <Result> information for the indicated number
   *    and return it as a DataObjectMapElement
   * 
   * @param oDbIndex
   * @param iNumber
   * @return 
   */
  private DataObject getOneResult(XmlResultDbase oDbIndex, int iNumber) {
    ByRef<XmlNode> ndxResult = new ByRef(null);
    DataObjectMapElement objResult = null;
    
    try {
      // Get the result id
      String sResId = String.valueOf(iNumber);
      // Get the resulting XmlNode
      if (oDbIndex.OneResult(ndxResult, sResId)) {
        // Check whether the returned value is not zero
        if (ndxResult.argValue!= null) {
          objResult = new DataObjectMapElement();
          // Get the attribute values
          objResult.put("ResId", sResId);
          objResult.put("File", ndxResult.argValue.getAttributeValue("File"));
          objResult.put("TextId", ndxResult.argValue.getAttributeValue("TextId"));
          objResult.put("Search", ndxResult.argValue.getAttributeValue("Search"));
          objResult.put("Cat", ndxResult.argValue.getAttributeValue("Cat"));
          objResult.put("sentId", ndxResult.argValue.getAttributeValue("forestId"));
          objResult.put("constId", ndxResult.argValue.getAttributeValue("eTreeId"));
          objResult.put("Notes", ndxResult.argValue.getAttributeValue("Notes"));
          objResult.put("SubType", ndxResult.argValue.getAttributeValue("Period"));
          objResult.put("Text", ndxResult.argValue.getAttributeValue("Text"));
          objResult.put("Psd", ndxResult.argValue.getAttributeValue("Psd"));
          objResult.put("Pde", ndxResult.argValue.getAttributeValue("Pde"));
          // Put the features into a DataObject
          DataObjectList arFeats = new DataObjectList("features");
          // Collect all the features
          List<XmlNode> lFeats = ndxResult.argValue.SelectNodes("./descendant::Feature");
          for (int i=0;i<lFeats.size();i++) {
            XmlNode ndxFeat = lFeats.get(i);
            arFeats.add(ndxFeat.getAttributeValue("Value"));
          }
          // Add the list of features for this particular result id
          objResult.put("Features", arFeats);
        }
      }

      // Return the result
      return objResult;
    } catch (Exception ex) {
      errHandle.DoError("RequestHandlerDbInfo/getOneResult", ex, RequestHandlerDbInfo.class);
      return null;
    }
  }
  
}
