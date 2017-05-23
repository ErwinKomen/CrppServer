/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.ru.crpx.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.search.Job;
import nl.ru.crpx.search.RunAny;
import nl.ru.crpx.search.WorkManager;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.util.json.JSONObject;
import org.apache.log4j.Logger;

/**
 * RequestHandlerStatusXl
 * Handle status information requests for "TxtList" jobs
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerStatusXl  extends RequestHandler {
  @SuppressWarnings("hiding")
  private static final Logger logger = Logger.getLogger(RequestHandlerDebug.class);

  public RequestHandlerStatusXl(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
  }
  
  @Override
  public DataObject handle() {
    String sCode = "";    // the "code" part
    String sResult = "";  // the "message" part
    int i;                // Counter
    String[] arArgObl = {"userid","jobid"};
    
    try {
      debug(logger, "REQ statusXl");
      // Get the JSON string argument we need to process, e.g:
      //   {  "userid": "erkomen", "jobid": "141" }
      sReqArgument = getReqString(request);
      logger.debug("Considering request /statusxl: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      // Look for obligatory arguments
      for (i=0;i<arArgObl.length;i++) {
        if (!jReq.has(arArgObl[i])) return DataObject.errorObject("INTERNAL_ERROR", 
                      "StatusXl misses obligatory argument ["+arArgObl[i]+"]");
      }
      // Get the obligatory parameters
      String sStatusXlUserId = jReq.getString("userid");
      String sStatusXlJobId = jReq.getString("jobid");
      // Get the status for this TxtList job from this user
      DataObjectMapElement objContent = new DataObjectMapElement();
      objContent.put("jobid", sStatusXlJobId);
      // Get a handle to the TxtList job
      WorkManager wrkMan = servlet.getWorkManager();
      workQueue = wrkMan.getWorkQueue(sStatusXlUserId);
      int iJobId = Integer.parseInt(sStatusXlJobId);
      RunAny search = workQueue.getRun(iJobId); 
      // Validate
      if (search == null) {
        String sMsg = "Cannot find Xl job #" + sStatusXlJobId + " for user [" + sStatusXlUserId + "]" +
                " Workman created ["+wrkMan.dateCreated()+"]";
        return DataObject.errorObject("INTERNAL_ERROR", sMsg);
      }
      // TODO: check if the indicated @userid has had anything to do with this job...
      
      // Action depends on the current status of the job
      if (search.finished()) {
        String sJobStatus = search.getJobStatus();
        sCode = sJobStatus;
        // Check if the status is error
        if (sJobStatus.equals("error")) {
          // Get the error message
          sResult = this.errorCollect(search);
        } else {
          sResult = "The search has finished";
          objContent.put("searchParam", searchParam.toDataObject());
          objContent.put("searchDone", search.finished());
          objContent.put("query", search.getJobQuery());        // The 'query' contains the original request parameters
          objContent.put("textlist", search.getJobBack());
          // Take over SOME values from the JobProgress
          JSONObject oProg = search.getJobCount();
          objContent.put("total", oProg.getInt("total"));       // Total number of files processed
        }
        // The job may be taken away
        workQueue.removeRun(search);
      } else {
        sCode = "working";
        sResult = "please wait";
        JSONObject oProg = search.getJobCount();
        objContent.put("total", oProg.getInt("total"));       // Total number of files processed
      }
      
    
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", sCode);
      objStatus.put("message", sResult);
      objStatus.put("userid", sStatusXlUserId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Getting the Xl job status failed", ex, RequestHandlerCrpList.class);
      return DataObject.errorObject("INTERNAL_ERROR", 
              "There was an error executing StatusXl");
    }
  }  
}
