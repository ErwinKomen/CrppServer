package nl.ru.crpx.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
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
 * RequestHandlerDebug
 * Load a CRP for a particular user (language independant)
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerLoad extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerLoad.class);
  // =================== Local variables =======================================
  private CrpManager crpManager;

  // =================== Initialisation of this class ==========================
  public RequestHandlerLoad(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
  }
  
  @Override
  public DataObject handle() {
    try {
      debug(logger, "REQ Load");
      // Get the project that should be loaded
      // Get the JSON string argument we need to process, e.g:
      //   {  "crp": "V2_versie11.crpx",
      //      "userid": "erkomen" }
      sReqArgument = getReqString(request);
      logger.debug("Considering request /load: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      String sCrpName = (jReq.has("crp")) ? jReq.getString("crp") : "";
      if (jReq.has("userid")) userId = jReq.getString("userid");
      sCurrentUserId = userId;
      // Get the CRP that is supposed to be executed (or load it if it is not loaded yet)
      prjThis = crpManager.getCrp(sCrpName,  sCurrentUserId);
      if (prjThis == null) {
        String sMsg;
        if (errHandle.hasErr())
          sMsg = "Errors: " + errHandle.getErrList().toString();
        else
          sMsg = "Could not initialize project [" + sCrpName + "] for user [" +
                  sCurrentUserId + "]";
        return DataObject.errorObject("INTERNAL_ERROR", sMsg);
      }
      
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "The CRP has been loaded: " + sCrpName);
      objStatus.put("userid", userId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Loading the CRP failed", ex, RequestHandlerLoad.class);
      return null;
    }
  }
}
