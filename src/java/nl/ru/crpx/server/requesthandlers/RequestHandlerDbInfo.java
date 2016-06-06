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
import net.sf.saxon.s9api.QName;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectList;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.project.CorpusResearchProject;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.crpx.xq.Extensions;
import nl.ru.util.ByRef;
import nl.ru.util.json.JSONObject;
import nl.ru.xmltools.XmlIndexTgReader;
import nl.ru.xmltools.XmlNode;
import nl.ru.xmltools.XmlResultPsdxIndex;
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
  private XmlIndexTgReader objXmlRdr=null;    // Index reader for current file
  private File objCurrentFile = null;       // File we are working on now
  private String loc_xpWords = "";          // Xpath expression to get to the words
  private Extensions ruExt = null;          // To make sure Extension functions work
  private static final QName loc_attr_LeafText = new QName("", "", "Text");

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
      //      "count": 50,          // number of hits (within the category)
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
      
      // Gain access to the database through a reader
      CorpusResearchProject oCrpx = new CorpusResearchProject(true);
      XmlResultPsdxIndex oDbIndex = new XmlResultPsdxIndex(oCrpx, null, errHandle);
      String sDbFile = "/etc/project/" + sCurrentUserId + "/dbase/" + sDbName;
      if (!oDbIndex.Prepare(sDbFile)) return DataObject.errorObject("availability", 
              "The database with the indicated name cannot be loaded for this user");
      
      // Start a content object
      DataObjectMapElement objContent = new DataObjectMapElement();
      
      // The general information of the database must be added at any rate (no matter the value of iUpdStart)
      objContent.put("General", getGeneralPart(oDbIndex));
      
      // Start an array with the required results
      DataObjectList arHitDetails = new DataObjectList("results");
      int iCount = 0;     // Number of results actually given

      // Find out what the operation is: if start less than zero, then provide the general info
      if (iUpdStart >=0 && iUpdCount>0) {
        // The user wants to have information starting at index [start]
        int iUpdEnd = iUpdStart + iUpdCount;
        for (int i=iUpdStart; i<iUpdEnd;i++) {
          // Collect and add the information of one result
          DataObjectMapElement oRes = (DataObjectMapElement) getOneResult(oDbIndex, i);
          if (oRes != null) { arHitDetails.add(oRes); iCount +=1; }
        }
      }
      // Add the number of results actually given
      objContent.put("Count", iCount);
      // Add the total number of results
      objContent.put("Size", oDbIndex.Size());
      // Add the array of results
      objContent.put("Results", arHitDetails);
      
       
 
      
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
  private DataObject getGeneralPart(XmlResultPsdxIndex oDbIndex) {
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
   * getOneResult --
   *    Retrieve the <Result> information for the indicated number
   *    and return it as a DataObjectMapElement
   * 
   * @param oDbIndex
   * @param iNumber
   * @return 
   */
  private DataObject getOneResult(XmlResultPsdxIndex oDbIndex, int iNumber) {
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
