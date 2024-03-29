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
import javax.servlet.http.Part;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectList;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.util.FileUtil;
import nl.ru.util.IoUtil;
import static nl.ru.util.StringUtil.decompressSafe;
import nl.ru.util.json.JSONObject;
//import java.util.logging.Logger;
import java.util.logging.Logger;


/**
 * RequestHandlerCrpSet
 *    Receive and store a crp (corpus research project) at the CrpServer
 *      for the indicated user
 * Params:
 *    name    - name of the crpx (with or without postfix .crpx)
 *    userid  - name of the user under which the .crpx is stored
 *    crp     - the xml text of the crpx file
 *    overwrite - boolean indicating that we may overwrite an existing copy
 * Optional:
 *    lng     - which language to link to the CRP
 *    dir     - which 'dir' within the language to link to it
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerCrpSet extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerCrpSet.class.getName());
  // =================== Local variables =======================================
  private CrpManager crpManager;

  // =================== Initialisation of this class ==========================
  public RequestHandlerCrpSet(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
  }
  
  @Override
  public DataObject handle() {
    boolean bOverwrite = true;
    String sCrpText = "";
    String sLng = "";           // Optional "crp" 
    String sDir = "";           // Optional "lng"
    
    try {
      debug(logger, "REQ crpset");
      // We are expecting a 'multipart' request consisting of two parts:
      //  "query" - a stringified JSON object (see below)
      //  "file"  - a file
      //
      // The JSON object "query" consists of:
      //   {  "userid": "erkomen",
      //      "crp":    "<crp>...</crp>",
      //      "name":   "ParticleA.crpx",
      //      "overwrite": true}
      sReqArgument = getReqString(request);
      debug(logger, "Considering request /crpset: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      if (!jReq.has("userid")) return DataObject.errorObject("syntax", 
          "The /crpset request must contain: userid.");
      sCurrentUserId = jReq.getString("userid");
      // Get the CRP text
      if (!jReq.has("crp"))return DataObject.errorObject("syntax", 
          "The /crpset request must contain: crp.");
      sCrpText = decompressSafe(jReq.getString("crp"));
      // ======= Debugging ============
      // debug(logger, "The crp contains:");
      // logger.debug(sCrpText);
      // debug(logger, "================");
      // ==============================
      
      // Possibly get overwrite parameter
      if (jReq.has("overwrite")) bOverwrite = jReq.getBoolean("overwrite");
      // Get optional "lng" and "dir" parameters
      if (jReq.has("lng")) sLng = jReq.getString("lng");
      if (jReq.has("dir")) sDir = jReq.getString("dir");
      // Check for the name of the crpx
      String sCrpName = "";
      if (!jReq.has("name")) return DataObject.errorObject("syntax", 
          "The /crpset request must contain: name.");
      sCrpName = jReq.getString("name");
      // Check if this has the .crpx ending
      if (!sCrpName.endsWith(".crpx")) sCrpName += ".crpx";
      if (!bOverwrite) {
        // Get a list of all this user's CRPs satisfying the name condition
        DataObjectList arCrpList = (DataObjectList) crpManager.getCrpList( sCurrentUserId, "", sCrpName);
        // Check if
        if (!arCrpList.isEmpty()) {
          return DataObject.errorObject("overwrite", 
            "A .crpx file called ["+sCrpName+"] is already present, and would be overwritten.");
        } 
        
      }
      // Save the CRP to an appropriate location
      String sProjectPath = RequestHandler.getCrpPath(sCrpName, sCurrentUserId);
      FileUtil.writeFile(sProjectPath, sCrpText, "utf-8");
      
      // Content part
      DataObjectMapElement objContent = new DataObjectMapElement();
      objContent.put("name", sCrpName);
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "The crp has been stored at the server");
      objStatus.put("userid", userId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Uploading a CRP failed", ex, RequestHandlerCrpSet.class);
      return null;
    }
  }
}
