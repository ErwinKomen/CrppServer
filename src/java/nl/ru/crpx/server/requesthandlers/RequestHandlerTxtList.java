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
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.search.RunTxtList;
import nl.ru.crpx.search.SearchManager;
import nl.ru.crpx.search.SearchParameters;
import nl.ru.crpx.search.WorkQueueXqF;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.util.json.JSONObject;
import java.util.logging.Logger;


/**
 * RequestHandlerTxtList
 * Give a list of all texts for a particular combination of Lng/Part
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerTxtList extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerTxtList.class.getName());
  // =================== Local variables =======================================
  private CrpManager crpManager;
  private SearchManager srchManager;

  // =================== Initialisation of this class ==========================
  public RequestHandlerTxtList(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
    this.srchManager = servlet.getSearchManager();
  }
  
  @Override
  public DataObject handle() {
    String sFilter = "*";   // Default filter
    String sLng = "eng_hist";
    String sDir = "";
    String sExt = "";
    String sJobId;

    try {
      debug(logger, "REQ TxtList");
      // Default values
      sReqArgument = getReqString(request);
      sCurrentUserId = "";
      // Get the JSON string arguments we need to process, e.g:
      //   {  "userid": "erkomen",
      //      "lng":    "eng_hist",   NOTE: May also be 'all'
      //      "dir":    "lModE",
      //      "ext":    "psdx"
      //   }
      // Note: if no user is given, then we should give all users and all crp's
      debug(logger, "Considering request /txtlist: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      // Look for obligatory arguments
      if (!jReq.has("lng")) return DataObject.errorObject("INTERNAL_ERROR", 
                    "TxtList misses obligatory argument [lng]");
      // Take the obligatory arguments
      sLng = jReq.getString("lng");
      // Look for optional arguments
      if (jReq.has("dir")) sDir = jReq.getString("dir");
      if (jReq.has("userid")) sCurrentUserId = jReq.getString("userid");
      if (jReq.has("ext")) sExt = jReq.getString("ext");
      // Check for a filter
      if (jReq.has("filter")) sFilter = jReq.getString("filter");
      // Prepare the search parameters
      SearchParameters searchTxtLpar = new SearchParameters(this.searchMan);
      searchTxtLpar.put("lng", sLng);
      searchTxtLpar.put("dir", sDir);
      searchTxtLpar.put("ext", sExt);
      // Create a new job
      RunTxtList oneTxtList = new RunTxtList(errHandle, null, sCurrentUserId, 
              this.srchManager, searchTxtLpar);
      
      // Start the job to get the textlist
      workQueue = servlet.getWorkManager().getWorkQueue(sCurrentUserId);
      // errHandle.debug("TxtList: Workman created ["+servlet.getWorkManager().dateCreated()+"]");
      try {
        // Start executing the TxtList function
        workQueue.execute(oneTxtList);
      } catch (Exception ex) {
        // If there is an error, then we return that
        return DataObject.errorObject("INTERNAL_ERROR", 
                "TxtList failed to execute txtlist job #"+
                oneTxtList.getJobId()+": "+ex.getMessage());
      }
      DataObjectMapElement objContent = new DataObjectMapElement();
      // Get the ID of the job that has now been started
      sJobId = oneTxtList.getJobId();
      objContent.put("jobid", sJobId);
      
      // Debugging: make the job id available for watchers
      errHandle.debug("/txtlist: jobid = "+sJobId);
            
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      // objStatus.put("code", "completed");
      objStatus.put("code", "started");
      objStatus.put("message", "See the list of texts in the [content] section");
      objStatus.put("userid", userId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Providing a text list failed", ex, RequestHandlerTxtList.class);
      return DataObject.errorObject("INTERNAL_ERROR", "TxtList failed: " + ex.getMessage());
    }
  }
}
