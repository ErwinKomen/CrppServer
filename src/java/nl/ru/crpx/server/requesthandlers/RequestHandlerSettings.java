package nl.ru.crpx.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.util.json.JSONObject;
import java.util.logging.Logger;

/*
 * This software has been developed at the "Meertens Instituut"
 *   for the CLARIN project "CorpusStudio-WebApplication".
 * The application is based on the "CorpusStudio" program written by Erwin R. Komen
 *   while working for the Radboud University Nijmegen.
 * The program and the source can be freely used and re-distributed.
 */


/**
 * RequestHandlerSettings
 *    Return the JSONObject containing the settings for the indicated user
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerSettings extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerSettings.class.getName());
  // =================== Local variables =======================================
  private CrpManager crpManager;

  // =================== Initialisation of this class ==========================
  public RequestHandlerSettings(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
  }
  
  @Override
  public DataObject handle() {
    try {
      debug(logger, "REQ Settings");
      // Get the JSON string argument we need to process, e.g:
      //   {  "userid": "erkomen" }
      // Note: if no user is given, then we should give all users and all crp's
      sReqArgument = getReqString(request);
      debug(logger, "Considering request /settings: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      // Validate obligatory parameters
      if (!jReq.has("userid")) return DataObject.errorObject("settings syntax", 
              "Need to provide [userid]");      
      // Retrieve obligatory parameter
      sCurrentUserId = jReq.getString("userid");
      // Look for optional parameter
      if (jReq.has("dbase")) {
        // Database settings are expected to follow
        JSONObject oDbase = jReq.getJSONObject("dbase");
        // Obligatory: name
        if (!oDbase.has("name")) return DataObject.errorObject("settings syntax", 
              "Object [dbase] needs [name]");      
        String sDbName = oDbase.getString("name");
        // Add the information that is being passed on
        crpManager.addUserSettingsDb(sCurrentUserId, sDbName, oDbase);
      }
      
      // Get the settings.json for this user as DataObject
      DataObject objContent = crpManager.getUserSettingsObject(sCurrentUserId);
      
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "See the settings object in the [content] section");
      objStatus.put("userid", sCurrentUserId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Providing a CRP list failed", ex, RequestHandlerSettings.class);
      return null;
    }
  }
}
