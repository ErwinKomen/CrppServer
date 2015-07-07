package nl.ru.crpx.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectList;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.util.FileUtil;
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
 * RequestHandlerCrpSet
 *    Receive and store a crp (corpus research project) at the CrpServer
 *      for the indicated user
 * Params:
 *    name    - name of the crpx (with or without postfix .crpx)
 *    userid  - name of the user under which the .crpx is stored
 *    crp     - the xml text of the crpx file
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerCrpSet extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerCrpSet.class);
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
    try {
      debug(logger, "REQ crpset");
      // Get the JSON string argument we need to process, e.g:
      //   {  "userid": "erkomen",
      //      "crp":    "<crp>...</crp>",
      //      "name":   "ParticleA.crpx",
      //      "overwrite": true}
      // Note: if no user is given, then we should give all users and all crp's
      sReqArgument = getReqString(request);
      logger.debug("Considering request /crpset: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      if (!jReq.has("userid")) return DataObject.errorObject("syntax", 
          "The /crpset request must contain: userid.");
      sCurrentUserId = jReq.getString("userid");
      // Check for the xml text, stored in "crp"
      String sCrpText = "";
      if (!jReq.has("crp")) return DataObject.errorObject("syntax", 
          "The /crpset request must contain: crp.");
      sCrpText = jReq.getString("crp");
      // Possibly get overwrite parameter
      if (jReq.has("overwrite")) bOverwrite = jReq.getBoolean("overwrite");
      // Check for the name of the crpx
      String sCrpName = "";
      if (!jReq.has("name")) return DataObject.errorObject("syntax", 
          "The /crpset request must contain: name.");
      sCrpName = jReq.getString("name");
      // Check if this has the .crpx ending
      if (!sCrpName.endsWith(".crpx")) sCrpName += ".crpx";
      if (!bOverwrite) {
        // Get a list of all this user's CRPs satisfying the name condition
        DataObjectList arCrpList = (DataObjectList) crpManager.getCrpList( sCurrentUserId, sCrpName);
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
