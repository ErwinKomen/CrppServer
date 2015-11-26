/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.ru.crpx.server.requesthandlers;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.search.Job;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.util.json.JSONObject;
import org.apache.log4j.Logger;

/**
 * RequestHandlerReset
 * Stop execution of indicated "Xq" job and all underlying XqF jobs
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerReset  extends RequestHandler {
  @SuppressWarnings("hiding")
  private static final Logger logger = Logger.getLogger(RequestHandlerDebug.class);

  public RequestHandlerReset(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
  }
  
  @Override
  public DataObject handle() {
    String sCode = "";    // the "code" part
    String sResult = "";  // the "message" part
    
    try {
      debug(logger, "REQ reset");
      // Get the JSON string argument we need to process, e.g:
      //   {  "userid": "erkomen", "jobid": "141" }
      sReqArgument = getReqString(request);
      logger.debug("Considering request /reset: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      // Get the userid and the jobid - both obligatory
      if (!jReq.has("userid")) return DataObject.errorObject("INTERNAL_ERROR", 
                    "A status request needs to have a [userid] specified");
      String sStatusUserId = jReq.getString("userid");
      if (!jReq.has("jobid")) return DataObject.errorObject("INTERNAL_ERROR", 
                    "A status request needs to have a [jobid] specified");
      String sStatusJobId = jReq.getString("jobid");
      
      
      // Get the status for this Xq job from this user
      DataObjectMapElement objContent = new DataObjectMapElement();
      objContent.put("jobid", sStatusJobId);
      // Get a handle to the Xq job
      Job search = searchMan.searchGetJobXq(sStatusJobId);
      // Validate
      if (search == null) return DataObject.errorObject("INTERNAL_ERROR", 
              "Cannot find job #" + sStatusJobId + " for user [" + sStatusUserId + "]");

      // Attempt to finish the job
      search.cancelJob();
      
      // Indicate what we have done
      objContent.put("action", "aborted");
      objContent.put("finished", search.finished()); 
      
    
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", sCode);
      objStatus.put("message", sResult);
      objStatus.put("userid", sStatusUserId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Ressting the Xq job failed", ex, RequestHandlerCrpList.class);
      return null;
    }
  }  
}
