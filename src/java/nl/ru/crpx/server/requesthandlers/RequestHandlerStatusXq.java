/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.ru.crpx.server.requesthandlers;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataFormat;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.search.Job;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.util.json.JSONObject;
import java.util.logging.Logger;

/**
 * RequestHandlerStatusXq
 * Handle status information requests for "Xq" jobs
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerStatusXq  extends RequestHandler {
  @SuppressWarnings("hiding")
  private static final Logger logger = Logger.getLogger(RequestHandlerDebug.class.getName());

  public RequestHandlerStatusXq(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
  }
  
  @Override
  public DataObject handle() {
    String sCode = "";    // the "code" part
    String sResult = "";  // the "message" part
    String sStatusJobId ="";
    
    try {
      debug(logger, "REQ statusXq");
      // Get the JSON string argument we need to process, e.g:
      //   {  "userid": "erkomen", "jobid": "141" }
      sReqArgument = getReqString(request);
      debug(logger, "Considering request /statusxq: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      // Get the userid and the jobid - both obligatory
      if (!jReq.has("userid")) return DataObject.errorObject("INTERNAL_ERROR", 
                    "A status request needs to have a [userid] specified");
      String sStatusUserId = jReq.getString("userid");
      if (!jReq.has("jobid")) return DataObject.errorObject("INTERNAL_ERROR", 
                    "A status request needs to have a [jobid] specified");
      // Make sure the jobid is a string...
      try {
        sStatusJobId = jReq.get("jobid").toString();
      } catch (Exception ex) {
        debug(logger, "The jobid must be a string");
        return DataObject.errorObject("INTERNAL_ERROR", "The jobid must be a string");
      }
      // Get the status for this Xq job from this user
      DataObjectMapElement objContent = new DataObjectMapElement();
      objContent.put("jobid", sStatusJobId);
      
      // Check if the user asks for a list or a specific (numeric) job id
      if (sStatusJobId.equals("list")) {
        // Get a list and put it in the content
        objContent.put("list", searchMan.jobList());
      } else {
        // Get a handle to the Xq job
        Job search = searchMan.searchGetJobXq(sStatusJobId);
        // Validate
        if (search == null) {
          debug(logger, "Cannot find job #" + sStatusJobId + " for user [" + sStatusUserId + "]");
          debug(logger, "Overview of current jobs:");
          debug(logger, searchMan.jobList().toString(DataFormat.JSON) );
          return DataObject.errorObject("INTERNAL_ERROR", 
                "Cannot find job #" + sStatusJobId + " for user [" + sStatusUserId + "]");
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
            objContent.put("searchTime", search.executionTimeMillis());
            objContent.put("searchDone", search.finished());
            objContent.put("query", search.getJobQuery());        // The 'query' contains the original request parameters
            objContent.put("taskid", search.getJobTaskId());
            objContent.put("table", search.getJobTable());        // PROBLEM: this table can become very large...
            // Take over SOME values from the JobProgress
            JSONObject oProg = search.getJobProgress();
            objContent.put("total", oProg.getInt("total"));       // Total number of files processed
          }
        } else {
          sCode = "working";
          sResult = "please wait";
          JSONObject oProg = search.getJobProgress();
          String sStatusStart = oProg.has("start") ? oProg.getString("start") : "-";
          String sStatusFinish = oProg.has("finish") ? oProg.getString("finish") : "-";
          objContent.put("start", sStatusStart);    // Name of most recently started file
          objContent.put("finish", sStatusFinish);  // Name of most recently finished file
          int iStatusCount = oProg.has("count") ? oProg.getInt("count") : 0;
          int iStatusTotal = oProg.has("total") ? oProg.getInt("total") : 0;
          int iStatusReady = oProg.has("ready") ? oProg.getInt("ready") : 0;
          int iStatusFound = oProg.has("found") ? oProg.getInt("found") : 0;
          objContent.put("count", iStatusCount);       // Number of files that have started
          objContent.put("total", iStatusTotal);       // Total number of files to be done
          objContent.put("ready", iStatusReady);       // Number of files ready
          objContent.put("found", iStatusFound);       // Number of hits total found
        }
      }
      
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
      errHandle.DoError("Getting the Xq job status failed", ex, RequestHandlerCrpList.class);
      return null;
    }
  }  
}
