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
  // =================== Local variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerDebug.class);
  
  // =================== Class initialisation
  public RequestHandlerExecute(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Possibly adapt the current user's ID
    if (request.getUserPrincipal()==null) {
      sCurrentUserId = request.getRemoteHost();
    } else {
      sCurrentUserId = request.getUserPrincipal().getName();      
    }
    
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
      logger.debug("XqJob query: " + sReqArgument);
      // Put the argument in the searchparameters
      searchParam.put("query", sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      // Get a possible userid
      if (jReq.has("userid")) userId = jReq.getString("userid");
      sCurrentUserId = userId;
      // Get the language index
      String sLng = "eng_hist";
      if (jReq.has("lng")) sLng = jReq.getString("lng");
      // Get the Corpus Research Project name
      if (!jReq.has("crp"))
        return DataObject.errorObject("INTERNAL_ERROR", 
                "No CRP name has been supplied");
      String sCrpName = jReq.getString("crp");
      // Try loading and initializing CRP
      if (!initCrp(sCrpName, sLng)) 
        return DataObject.errorObject("INTERNAL_ERROR", 
                "Could not load the indicated project");
      // Get the directory associated with the Language Index
      File fDir = servlet.getSearchManager().getIndexDir(sLng);
      if (fDir==null) 
        return DataObject.errorObject("INTERNAL_ERROR", 
                "No language directory associated with [" + sLng + "]");
      // We need the directory as a string
      String sTarget = fDir.getAbsolutePath();
      // Get a sub directory or focus file
      String sFocus = "";
      if (jReq.has("dir")) sFocus = jReq.getString("dir");
      if (!sFocus.isEmpty()) {
        // Locate this part 'under' the language index directory
        Path pStart = Paths.get(sTarget);
        List<String> lInputFiles = new ArrayList<>();
        FileUtil.getFileNames(lInputFiles, pStart, sFocus);
        // Validate result
        if (lInputFiles.isEmpty()) 
          return DataObject.errorObject("INTERNAL_ERROR", 
                  "Cannot find input file for language [" + sLng + "] and dir=[" + sFocus + "]");
        // If anything comes out, then take only the *FIRST* hit!!!!
        sTarget = lInputFiles.get(0);
      }
      // Set the correct source 
      prjThis.setSrcDir(new File(sTarget));
    
      // Get a list of "qxjob" items belonging to the current user
      List<Long> userJobIds = Job.getUserJobList(sCurrentUserId, "jobxq");
      // Walk the list and see if there are still active ones
      if (userJobIds != null) {
        for (long iThisJobId: userJobIds) {
          // Get the job belonging to this one
          Job userJob = searchMan.searchGetJobXq(String.valueOf(iThisJobId));
          if (userJob != null) {
            // ================= Debugging ========================
            // Check how many users are still waiting for this job
            if (!userJob.finished() && userJob.getClientsWaiting()<=1) {
              logger.debug("Xqjob decreasing for: " + iThisJobId);
              // Signal the Xq jobs and XqF jobs that they should stop
              // TODO: any code here

              // Decrease the number of clients waiting for this job to finish
              userJob.changeClientsWaiting(-1);
              // ================= Debugging ========================
              logger.debug("Xqjob reduced clients for: " + iThisJobId);
              // Make the job un-reusable
              userJob.setUnusable();
            }
          }
        }
      }

      // Create a job for this query
      Job search;
      // Initiate the search
      search = searchMan.searchXq(prjThis, sCurrentUserId, searchParam);
      // Get the @id of the job that has been created
      String sThisJobId = search.getJobId();
      String sNow = Job.getCurrentTimeStamp();
      // Additional debugging to find out where the errors come from
      errHandle.debug("Xqjob [" + sNow + "] userid=[" + sCurrentUserId + "] jobid=[" + 
              sThisJobId + "], finished=" + 
              search.finished() + " status=" + search.getJobStatus() );
      
      // If search is not done yet, indicate this to the user
      if (!search.finished()) {
        return DataObject.statusObject("started", "Searching, please wait...", sCurrentUserId, sThisJobId, 
                servlet.getSearchManager().getCheckAgainAdviceMinimumMs());
      }

      // Search is done; Create a JSONObject with the correct status and content parts

      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "The Java-part of the R-webservice works fine.");
      objStatus.put("userid", sCurrentUserId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("status", objStatus);
      return response;
    } catch (QueryException | InterruptedException ex) {
      errHandle.DoError("Executing the query failed", ex, RequestHandlerExecute.class);
      return null;
    }
  }  
}
