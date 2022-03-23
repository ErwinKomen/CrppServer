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

import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectList;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.util.FileUtil;
import static nl.ru.util.StringUtil.decompressSafe;
import nl.ru.util.json.JSONObject;
import java.util.logging.Logger;


/**
 * RequestHandlerDbSet
 *    Receive and store a Result Database at the CrpServer
 *      for the indicated user
 * Params:
 *    name    - name of the xml Result Dabase (with or without postfix .xml)
 *    userid  - name of the user under which the dbase is stored
 *    crp     - the xml text of the dbase file
 *    overwrite - boolean indicating that we may overwrite an existing copy
 * Optional:
 *    lng     - which language to link to the dbase
 *    dir     - which 'dir' within the language to link to it
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerDbSet extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerDbSet.class.getName());
  // =================== Local variables =======================================
  private CrpManager crpManager;

  // =================== Initialisation of this class ==========================
  public RequestHandlerDbSet(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
  }
  
  @Override
  public DataObject handle() {
    boolean bOverwrite = true;
    String sDbText = "";
    String sLng = "";           // Optional "language" (corpus)
    String sDir = "";           // Optional "part" within the language
    
    try {
      debug(logger, "REQ dbset");
      // The JSON object "query" consists of:
      //   {  "userid": "erkomen",
      //      "db":     "<Result>...</Result>",
      //      "name":   "ParticleA_Dbase.xml",
      //      "overwrite": true}
      sReqArgument = getReqString(request);
      debug(logger, "Considering request /dbset: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      if (!jReq.has("userid")) return DataObject.errorObject("syntax", 
          "The /dbset request must contain: userid.");
      sCurrentUserId = jReq.getString("userid");
      // Get the CRP text
      if (!jReq.has("db"))return DataObject.errorObject("syntax", 
          "The /dbset request must contain: db.");
      sDbText = decompressSafe(jReq.getString("db"));
      // ======= Debugging ============
      // debug(logger, "The crp contains:");
      // logger.debug(sDbText);
      // debug(logger, "================");
      // ==============================
      
      // Possibly get overwrite parameter
      if (jReq.has("overwrite")) bOverwrite = jReq.getBoolean("overwrite");
      // Get optional "lng" and "dir" parameters
      if (jReq.has("lng")) sLng = jReq.getString("lng");
      if (jReq.has("dir")) sDir = jReq.getString("dir");
      // Check for the name of the crpx
      String sDbName = "";
      if (!jReq.has("name")) return DataObject.errorObject("syntax", 
          "The /dbset request must contain: name.");
      sDbName = jReq.getString("name");
      // Check if this has the .xml ending
      if (!sDbName.endsWith(".xml")) sDbName += ".xml";
      if (!bOverwrite) {
        // Get a list of all this user's Result Databases satisfying the name condition
        DataObjectList arDbList = (DataObjectList) crpManager.getDbList( sCurrentUserId, sDbName);
        // Check if
        if (!arDbList.isEmpty()) {
          return DataObject.errorObject("overwrite", 
            "An .xml file called ["+sDbName+"] is already present, and would be overwritten.");
        } 
        
      }
      // Save the Result Dbase to an appropriate location
      String sResDbase = RequestHandler.getDbFilename(sDbName, sCurrentUserId);
      FileUtil.writeFile(sResDbase, sDbText, "utf-8");
      
      // Content part
      DataObjectMapElement objContent = new DataObjectMapElement();
      objContent.put("name", sDbName);
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "The result dbase has been stored at the server: "+sResDbase);
      objStatus.put("userid", userId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Uploading a Result Dbase failed", ex, RequestHandlerDbSet.class);
      return null;
    }
  }
}
