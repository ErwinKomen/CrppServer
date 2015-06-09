/*
 * This software has been developed at the "Meertens Instituut"
 *   for the CLARIN project "CorpusStudio-WebApplication".
 * The application is based on the "CorpusStudio" program written by Erwin R. Komen
 *   while working for the Radboud University Nijmegen.
 * The program and the source can be freely used and re-distributed.
 */
package nl.ru.crpx.server.requesthandlers;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.search.Job;
import nl.ru.crpx.search.QueryException;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.util.FileUtil;
import nl.ru.util.json.JSONObject;
import org.apache.log4j.Logger;

/**
 * RequestHandlerExecute
 *    
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerExecute extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerDebug.class);
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
    try {
      // Show where we are
      debug(logger, "REQ execute");
      // Get the project that should be loaded and executed
      // Get the JSON string argument we need to process, e.g:
      //   {  "lng": "eng_hist",
      //      "crp": "V2_versie11.crpx",
      //      "dir": "OE",
      //      "userid": "erkomen" }
      sReqArgument = getReqString(request);
      logger.debug("Considering request /exe: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      String sLng = (jReq.has("lng")) ? jReq.getString("lng") : "eng_hist";
      String sCrpName = (jReq.has("crp")) ? jReq.getString("crp") : "";
      if (jReq.has("userid")) userId = jReq.getString("userid");
      sCurrentUserId = userId;
      String sFocus = (jReq.has("dir")) ? jReq.getString("dir") : "";
      String sSave = getCrpSaveDate(sCrpName, sCurrentUserId);
      
      // Create the query parameters myself: lng, crp, dir, userid, save
      JSONObject oQuery = new JSONObject();
      oQuery.put("lng", sLng);
      oQuery.put("crp", sCrpName);
      oQuery.put("dir", sFocus);
      oQuery.put("userid", (jReq.has("userid")) ? jReq.getString("userid") : "");
      oQuery.put("save", sSave);
      // The 'query' consists of [lng, crp, dir, userid, save]
      String sNewQuery = oQuery.toString();
      
      // Create a job for this query; this might contain an existing query
      Job search;
      String sThisJobId;
      
      // Check if this query is being executed or is available already
      search = searchMan.getXqJob(sNewQuery);
      if (search != null && !search.getJobStatus().equals("error")) {
        // Get the id of the job
        sThisJobId = search.getJobId();
      } else {
        // Okay, go ahead: first remove any running queries of the same user
        if (!removeRunningQueriesOfUser(sNewQuery)) return DataObject.errorObject("INTERNAL_ERROR", 
                  "There was a problem removing running queries of the current user");

        // Get the Corpus Research Project name
        if (!jReq.has("crp")) return DataObject.errorObject("INTERNAL_ERROR", 
                  "No CRP name has been supplied");

        // ============= Try loading and initializing CRP =====================

        // Get the CRP that is supposed to be executed (or load it if it is not loaded yet)
        prjThis = crpManager.getCrp(sCrpName, sCurrentUserId);
        if (prjThis == null) {
          String sMsg;
          if (errHandle.hasErr())
            sMsg = "Errors: " + errHandle.getErrList().toString();
          else
            sMsg = "Could not initialize project [" + sCrpName + "] for user [" +
                    sCurrentUserId + "]";
          return DataObject.errorObject("INTERNAL_ERROR", sMsg);
        }
        // ====================================================================
        // Add the project name as parameter (but not as "query"!!!)
        this.searchParam.put("name", sCrpName);

        // Get the directory associated with the Language Index
        File fDir = servlet.getSearchManager().getIndexDir(sLng);
        if (fDir==null) return DataObject.errorObject("INTERNAL_ERROR", 
                  "No language directory associated with [" + sLng + "]");
        // We need the directory as a string
        String sTarget = fDir.getAbsolutePath();
        // Get a sub directory or focus file
        if (!sFocus.isEmpty()) {
          // Locate this part 'under' the language index directory
          Path pStart = Paths.get(sTarget);
          List<String> lInputFiles = new ArrayList<>();
          FileUtil.getFileNames(lInputFiles, pStart, sFocus);
          // Validate result
          if (lInputFiles.isEmpty()) 
            return DataObject.errorObject("INTERNAL_ERROR", 
                    "Cannot find input file for language [" + sLng + "] and dir=[" + sFocus + "]\n" + 
                    "Looking in: " + pStart.toString());
          // If anything comes out, then take only the *FIRST* hit!!!!
          sTarget = lInputFiles.get(0);
        }
        // Set the correct source 
        prjThis.setSrcDir(new File(sTarget));
        
        // Set the @searchParam correct
        searchParam.put("query", sNewQuery);

        // Initiate the search by invoking "searchXq"
        search = searchMan.searchXq(prjThis, sCurrentUserId, searchParam);
        
        // Get the @id of the job that has been created
        sThisJobId = search.getJobId();
        String sNow = Job.getCurrentTimeStamp();
        // Additional debugging to find out where the errors come from
        errHandle.debug("Xqjob creation: [" + sNow + "] userid=[" + sCurrentUserId + "] jobid=[" + 
                sThisJobId + "], finished=" + 
                search.finished() + " status=" + search.getJobStatus() );

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
        objStatus.put("message", errHandle.getErrList().toString());
        search.getJobPtc();
        search.getJobProgress();
      } else {
        // Search is done; Create a JSONObject with the correct status and content parts
        // String sCount = search.getJobCount().toString();
        // String sRes = search.getJobResult();
        // The objContent (done last because the count might be done by this time)
        // objContent.put("count", sCount);
        // objContent.put("table", sRes);
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
              logger.debug("Xqjob reduced clients for: " + iThisJobId);
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
