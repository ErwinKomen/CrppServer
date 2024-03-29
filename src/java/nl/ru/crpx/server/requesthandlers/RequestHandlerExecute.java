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
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.search.Job;
import nl.ru.crpx.search.QueryException;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.crpx.tools.ErrHandle;
import nl.ru.util.ByRef;
import nl.ru.util.json.JSONObject;
import java.util.logging.Logger;

/**
 * RequestHandlerExecute
 *    
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerExecute extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerDebug.class.getName());
  // =================== Local variables =======================================
  private CrpManager crpManager;
  
  // =================== Class initialisation
  public RequestHandlerExecute(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Possibly adapt the current user's ID
    if (request.getUserPrincipal()==null) {
      sCurrentUserId = request.getRemoteHost();
    } else {
      sCurrentUserId = request.getUserPrincipal().getName();      
    }
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
  }
  
  @Override
  public DataObject handle() {
    JSONObject jReq;      // The request object
    JSONObject oOptions;  // The search options
    String sLng = "";     // Chosen language
    String sCrpName = ""; // Name of the CRP
    
    try {
      // Show where we are
      debug(logger, "REQ execute");
      // Get the project that should be loaded and executed
      // Get the JSON string argument we need to process, e.g:
      //   {  "lng": "eng_hist",
      //      "crp": "V2_versie11.crpx",
      //      "dir": "OE",
      //      "dbase": "bladi.xml",
      //      "options": {},
      //      "cache": false,
      //      "userid": "erkomen" }
      sReqArgument = getReqString(request);
      debug(logger, "Considering request /exe: " + sReqArgument);
      // Check for empty string
      if (sReqArgument.isEmpty()) {
        return DataObject.errorObject("INTERNAL_ERROR", 
          "The /exe request should at least have one JSON string parameter, optionally preceded by 'query='. ");
      }
      // Take apart the request object
      try {
        jReq = new JSONObject(sReqArgument);
      } catch (Exception ex) {
        return DataObject.errorObject("Argument error", 
          "Cannot interpret /exe request ["+ sReqArgument +"]");
      }
      sLng = (jReq.has("lng")) ? jReq.getString("lng") : "eng_hist";
      // Test for CRP presence
      if (!jReq.has("crp"))
        return DataObject.errorObject("Argument error", 
          "An /exe request should contain the name of the CRP to be executed");
      // Now get the CRP name safely
      sCrpName = jReq.getString("crp");
      // Add the extension of needed
      if (!sCrpName.endsWith(".crpx")) sCrpName += ".crpx";
      // Test for userid
      if (jReq.has("userid")) userId = jReq.getString("userid");
      sCurrentUserId = userId;
      String sFocus = (jReq.has("dir")) ? jReq.getString("dir") : "";
      // Get optional database input
      String sDbase = (jReq.has("dbase")) ? jReq.getString("dbase") : "";
      // Normally do caching
      boolean bCache = (jReq.has("cache")) ? jReq.getBoolean("cache") : true;
      // NOTE: the save DATE is the date when the CRP file was saved (on the server)
      String sSave = getCrpSaveDate(sCrpName, sCurrentUserId);
      // Double checking
      if (jReq.has("cache")) {
        boolean bValue = jReq.getBoolean("cache");
        debug(logger, "Cache boolean = " + bValue );
      } else {
        debug(logger, "Cache is not defined!!! jReq=" + jReq.toString());
      }
      
      // Options
      if (jReq.has("options")) {
        oOptions = jReq.getJSONObject("options");
      } else {
        oOptions = null;
      }
      
      // User settings: connect "crp" with "lng+dir"
      crpManager.addUserSettingsCrpLng(sCurrentUserId, sCrpName, sLng, sFocus);
      // Set this CR as the most recent project
      crpManager.addUserSettings(sCurrentUserId, "recent", sCrpName);
      
      // Create the query parameters myself: lng, crp, dir, dbase, userid, save
      JSONObject oQuery = new JSONObject();
      oQuery.put("lng", sLng);
      oQuery.put("crp", sCrpName);
      oQuery.put("dir", sFocus);
      if (oOptions != null) {oQuery.put("options", oOptions); }
      oQuery.put("dbase", sDbase);
      oQuery.put("userid", (jReq.has("userid")) ? jReq.getString("userid") : "");
      oQuery.put("save", sSave);  // Save date of the CRP!!
      // The 'query' consists of [lng, crp, dir, userid, save]
      //    and possibly [options]
      String sNewQuery = oQuery.toString();
      
      // Create a job for this query; this might contain an existing query
      Job search;
      String sThisJobId;
      
      // Check if this query is being executed or is available already
      search = (bCache) ? searchMan.getXqJob(sNewQuery) : null;
      // Note: if "cache" has been set to "false", then we should not consider the previous job
      if (search != null && !search.getJobStatus().equals("error")) {
        // Get the id of the job
        sThisJobId = search.getJobId();
        // Indicate that we are using a job from the cache
        debug(logger, "ReqHandleExe: re-using job #" + sThisJobId);
      } else {
        // Okay, go ahead: first remove any running queries of the same user
        if (!removeRunningQueriesOfUser(sNewQuery)) return DataObject.errorObject("INTERNAL_ERROR", 
                  "There was a problem removing running queries of the current user");

        // Get the Corpus Research Project name
        if (!jReq.has("crp")) return DataObject.errorObject("INTERNAL_ERROR", 
                  "No CRP name has been supplied");

        // ============= Try loading and initializing CRP =====================

        // Get the CRP that is supposed to be executed (or load it if it is not loaded yet)
        ByRef<ErrHandle> errCrp = new ByRef(null);
        if (bCache) {
          prjThis = crpManager.getCrp(sCrpName, sCurrentUserId, errCrp);
        } else {
          prjThis = crpManager.getCrpNoCache(sCrpName, sCurrentUserId, errCrp);
        }
        if (prjThis == null || errHandle.bInterrupt || errCrp.argValue.bInterrupt ||
                errCrp.argValue.hasErr()) {
          String sMsg = "";
          if (errHandle.hasErr() || errCrp.argValue.hasErr()) {
            if (errHandle.hasErr()) sMsg = errHandle.getErrList().toString();
            if (!sMsg.isEmpty()) sMsg += "\n";
            sMsg = "Errors: " + sMsg + errCrp.argValue.getErrList();
          } else
            sMsg = "Could not initialize project [" + sCrpName + "] for user [" +
                    sCurrentUserId + "]";
          return DataObject.errorObject("INTERNAL_ERROR", sMsg);
        }
        // Make sure the Corpus Research Project gets the correct WorkQueue
        prjThis.setWorkQueue(servlet.getWorkManager().getWorkQueue(sCurrentUserId));
        
        // ====================================================================
        // Add the project name as parameter (but not as "query"!!!)
        this.searchParam.put("name", sCrpName);

        // Set the language and part values
        prjThis.setLanguage(sLng);    // Note: the language is only set if the correct SETTING is available
        prjThis.setPart(sFocus);
        if (!sDbase.isEmpty()) {
          prjThis.setDbaseInput("True");  // Indicate that there is database input
          prjThis.setSource(sDbase);      // Set the database to be used as input
        } else
          prjThis.setDbaseInput("False"); // Indicate specifically that there is no database input
        
        // Get the directory associated with "lng" and "dir"
        String sTarget = servlet.getSearchManager().getCorpusPartDir(sLng, sFocus);
        // Validate
        if (sTarget.isEmpty()) 
           return DataObject.errorObject("INTERNAL_ERROR", 
                    "Cannot find input file for language [" + sLng + "] and dir=[" + sFocus + "]");
        // Set the correct source 
        prjThis.setSrcDir(new File(sTarget));
        if (!prjThis.getSrcDir().exists())
           return DataObject.errorObject("Corpus error", 
                    "The requested 'dir' part for the corpus does not exist on the server");
        
        // Set the @searchParam correct
        searchParam.put("query", sNewQuery);
        
        // Check if there are any previous results for this job that can be re-used
        if (bCache && prjThis.hasResults(oQuery)) {
          // Initiate a result-fetch job
          errHandle.debug("ReUsejob calling searchXqReUse");
          search = searchMan.searchXqReUse(prjThis, sCurrentUserId, searchParam);
          // Check for returns with errors
          if (errHandle.bInterrupt || errHandle.hasErr()) {
            return DataObject.errorObject("Re-used exe problem", 
                     errHandle.getErrList().toString());
          }
          // Get the @id of the job that has been created
          sThisJobId = search.getJobId();
          String sNow = Job.getCurrentTimeStamp();
          // Additional debugging to find out where the errors come from
          errHandle.debug("ReUsejob creation: [" + sNow + "] userid=[" + sCurrentUserId + "] jobid=[" + 
                  sThisJobId + "], finished=" + 
                  search.finished() + " status=" + search.getJobStatus() );
        } else {
          // Initiate the search by invoking "searchXq"
          search = searchMan.searchXq(prjThis, sCurrentUserId, searchParam, bCache);

          // Get the @id of the job that has been created
          sThisJobId = search.getJobId();
          String sNow = Job.getCurrentTimeStamp();
          // Additional debugging to find out where the errors come from
          errHandle.debug("Xqjob creation: [" + sNow + "] userid=[" + sCurrentUserId + "] jobid=[" + 
                  sThisJobId + "], finished=" + 
                  search.finished() + " status=" + search.getJobStatus() );
        }

      }

      // If search is not done yet, indicate this to the user
      if (!search.finished()) {
        // Check if more information is available or not
        String sMsg = "Searching, please wait...";
        if (search.getJobProgress().has("start")) {
          sMsg = search.getJobProgress().toString();
        } 
        return DataObject.statusObject("started", sMsg, sCurrentUserId, sThisJobId, 
                servlet.getSearchManager().getCheckAgainAdviceMinimumMs());
      }

      // The 'content' and the 'status' can be filled in to some extent...
      DataObjectMapElement objContent = new DataObjectMapElement();
      objContent.put("searchParam", searchParam.toDataObject());
      objContent.put("searchTime", search.executionTimeMillis());
      objContent.put("searchDone", search.finished());
      objContent.put("taskid", search.getJobTaskId());
      objContent.put("jobid", sThisJobId);
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("userid", sCurrentUserId);
      
      // The search is 'finished', but has it ended correctly?
      if (search.getJobStatus().equals("error")) {
        // Prepare a status object to return
        objStatus.put("code", "error");
        // Collect all error messages together
        String sMsg = this.errorCollect(search);
        // Pass on the error message
        objStatus.put("message", sMsg);
        search.getJobPtc();
        search.getJobProgress();
      } else {
        // Search is done; Create a JSONObject with the correct status and content parts
        objContent.put("table", search.getJobTable());

        // Prepare a status object to return
        objStatus.put("code", "completed");
        objStatus.put("message", "The search has finished fine.");
      }
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (QueryException | InterruptedException ex) {
      errHandle.DoError("Executing the query failed", ex, RequestHandlerExecute.class);
      return null;
    }
  }  
  
  /**
   * Remove any queries of the current user that are not equal to this
   *   new query and that are currently running
   * 
   * @param sNewQuery 
   */
  private boolean removeRunningQueriesOfUser(String sNewQuery) {
    try {
      // Test for situation: 'new job while old is still running'
      // - User starts new job 'B'
      // - User has jobs 'A' ... running, and
      //   a.  'A' does not equal 'B'
      //       Action: stop job 'A' and all child XqF jobs too
      //   b.  'A' equals 'B' (status is irrelevant at this point)
      //       Action: continue
      // Get a list of "XqJob" items belonging to the current user
      List<Long> userJobIds = Job.getUserJobList(sCurrentUserId, "jobxq");
      // Walk the list and see if there are still active ones
      if (userJobIds != null) {
        for (long iThisJobId: userJobIds) {
          // Get the job belonging to this one
          Job userJob = searchMan.searchGetJobXq(String.valueOf(iThisJobId));
          if (userJob != null) {
            // Get the query belonging to this job
            String sOldQuery = userJob.getJobQuery();
            // Check if jobs are the same
            boolean bJobsAreSame = sOldQuery.equals(sNewQuery);
            // Note: job reduction occurs:
            //       - old job 'A' is still running
            //       - user comes up with a new job 'B'
            if (!bJobsAreSame && !userJob.finished() && userJob.getClientsWaiting()<=1) {
              // Decrease the number of clients waiting for this job to finish
              userJob.changeClientsWaiting(-1);
              // ================= Debugging ========================
              debug(logger, "Xqjob reduced clients for: " + iThisJobId);
              // Make the job un-reusable
              userJob.setUnusable();
              // Since this Xq job is now being finished, its child XqF jobs should also be finished
              searchMan.finishChildXqFjobs(userJob);
            }
          }
        }
      }
      // Return positively
      return true;
    } catch (Exception ex) {
      return errHandle.DoError("Could not remove stale jobs", ex, RequestHandlerExecute.class);
    }
  }
}
