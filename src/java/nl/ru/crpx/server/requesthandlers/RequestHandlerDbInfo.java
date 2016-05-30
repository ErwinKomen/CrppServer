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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import nl.ru.crpx.dataobject.DataFormat;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectList;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.dataobject.DataObjectString;
import nl.ru.crpx.project.CorpusResearchProject;
import nl.ru.crpx.project.CorpusResearchProject.ProjType;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.crpx.xq.CrpFile;
import nl.ru.crpx.xq.Extensions;
import nl.ru.util.FileUtil;
import nl.ru.util.StringUtil;
import nl.ru.util.json.JSONArray;
import nl.ru.util.json.JSONObject;
import nl.ru.xmltools.Parse;
import nl.ru.xmltools.XmlAccess;
import nl.ru.xmltools.XmlAccessFolia;
import nl.ru.xmltools.XmlAccessPsdx;
import nl.ru.xmltools.XmlDocument;
import nl.ru.xmltools.XmlIndexTgReader;
import nl.ru.xmltools.XmlResultPsdxIndex;
import org.apache.log4j.Logger;

/**
 * RequestHandlerDbInfo
 *    Provide detailed information about a search job that has (already)
 *    been executed. The kind of information to be provided depends on the 
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
      
      // Start an array with the required results
      DataObjectList arHitDetails = new DataObjectList("content");

      // Find out what the operation is: if start less than zero, then provide the general info
      if (iUpdStart<0) {
        // THe user wants to have all the information in the <General> section
        JSONObject oHdr = oDbIndex.headerInfo();
        // Make sure certain parts are copied
        String sProjectName = ""; // Name of CRPX that created the DB
        String sCreated = "";     // Created date in sortable date/time
        String sLanguage = "";    // Main language
        String sPart = "";        // Part of corpus
        String sNotes = "";       // Notes to the DB
        String sAnalysis = "";    // Names of all features used
        if (oHdr.has("ProjectName")) sProjectName = oHdr.getString("ProjectName");
        if (oHdr.has("Created")) sCreated = oHdr.getString("Created");
        if (oHdr.has("Language")) sLanguage = oHdr.getString("Language");
        if (oHdr.has("Part")) sPart = oHdr.getString("Part");
        if (oHdr.has("Notes")) sNotes = oHdr.getString("Notes");
        if (oHdr.has("Analysis")) sAnalysis = oHdr.getString("Analysis");
        // Put all into a datamapelement
        DataObjectMapElement oGeneral = new DataObjectMapElement();
        oGeneral.put("ProjectName", sProjectName);
        oGeneral.put("Created", sCreated);
        oGeneral.put("Language", sLanguage);
        oGeneral.put("Part", sPart);
        oGeneral.put("Notes", sNotes);
        oGeneral.put("Analysis", sAnalysis);
        // Add the information as one item to [arHitDetail], which is returned in [content]
        arHitDetails.add(oGeneral);
      } else {
        // The user wants to have information starting at index [start]
        // TODO: implement this
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
      errHandle.DoError("Providing /dbinfo information failed", ex, RequestHandlerDbInfo.class);
      return null;
    }
  }
  
}
