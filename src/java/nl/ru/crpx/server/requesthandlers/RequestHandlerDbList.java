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
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.util.json.JSONArray;
import nl.ru.util.json.JSONObject;
import java.util.logging.Logger;


/**
 * RequestHandlerDbList
 * Give a list of all Corpus Databases available for a particular user (language independant)
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerDbList extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerDbList.class.getName());
  // =================== Local variables =======================================
  private CrpManager crpManager;

  // =================== Initialisation of this class ==========================
  public RequestHandlerDbList(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
  }
  
  @Override
  public DataObject handle() {
    try {
      debug(logger, "REQ DbList");
      // Get the JSON string argument we need to process, e.g:
      //   {  "userid": "erkomen" }
      // Note: if no user is given, then we should give all users and all crp's
      sReqArgument = getReqString(request);
      debug(logger, "Considering request /dblist: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      if (jReq.has("userid")) 
        sCurrentUserId = jReq.getString("userid");
      else
        sCurrentUserId = "";
      // Check for a filter
      String sFilter = "*.xml";
      if (jReq.has("filter"))
        sFilter = jReq.getString("filter");
      // Get a list of all the databases available for the indicated user
      DataObject objContent = crpManager.getDbList( sCurrentUserId, sFilter);
      
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "See the list of data bases in the [content] section");
      objStatus.put("userid", userId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Providing a dbase list failed", ex, RequestHandlerDbList.class);
      return null;
    }
  }
}
