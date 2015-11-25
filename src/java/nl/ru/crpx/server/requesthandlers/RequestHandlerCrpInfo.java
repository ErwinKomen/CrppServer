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
import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.project.CorpusResearchProject;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.util.DateUtil;
import nl.ru.util.json.JSONObject;
import org.apache.log4j.Logger;


/**
 * RequestHandlerCrpInfo
 * Give information on the indicated CRP
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerCrpInfo extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerCrpInfo.class);
  // =================== Local variables =======================================
  private CrpManager crpManager;

  // =================== Initialisation of this class ==========================
  public RequestHandlerCrpInfo(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
  }
  
  @Override
  public DataObject handle() {
    CorpusResearchProject crpInfo;
    
    try {
      debug(logger, "REQ CrpInfo");
      // Get the JSON string argument we need to process, e.g:
      //   {  "userid": "erkomen", "crp": "name_of_crp", "info": "modified" }
      // Note: if no user is given, then we should give all users and all crp's
      sReqArgument = getReqString(request);
      logger.debug("Considering request /crpinfo: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
                  
      // Validate the parameters
      if (!jReq.has("userid"))  return DataObject.errorObject("syntax", "The /crpinfo request must contain: userid.");
      if (!jReq.has("crp"))     return DataObject.errorObject("syntax", "The /crpinfo request must contain: crp (name of the crp).");
      if (!jReq.has("info"))    return DataObject.errorObject("syntax", "The /crpinfo request must contain: info (information type requested).");
      
      // Retrieve and process the parameters 
      sCurrentUserId = jReq.getString("userid");
      // Get the CRP NAME
      String sCrpName = jReq.getString("crp");
      // Get the type of info needed
      String sInfo = jReq.getString("info");
      
      // Make room for the information
      DataObjectMapElement objContent = new DataObjectMapElement();
      
      // Action depends on the type of information needed
      switch(sInfo) {
        case "modified":
          // Get the indicated CRP
          crpInfo = crpManager.getCrp(sCrpName, sCurrentUserId);
          // Validate
          if (crpInfo == null) return DataObject.errorObject("fatal", "The CRP is not known for this user: "+sCrpName+".");
          // Get a handle to the file
          File fCrp = new File(crpInfo.getLocation());
          // Get the modified date
          String sModified = DateUtil.dateToString(fCrp.lastModified());
          objContent.put("modified", sModified);
          break;
        case "dateChanged":
          // Get the indicated CRP
          crpInfo = crpManager.getCrp(sCrpName, sCurrentUserId);
          // Validate
          if (crpInfo == null) return DataObject.errorObject("fatal", "The CRP is not known for this user: "+sCrpName+".");
          // Get the value of the "Changed" setting within the CRP
          objContent.put("dateChanged", DateUtil.dateToString(crpInfo.getDateChanged()));
          break;
        default:
          // Unknown request
          return DataObject.errorObject("syntax", "The /crpinfo contains an unknown info type: "+sInfo+".");
      }      

      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "See the information in the [content] section");
      objStatus.put("userid", userId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Providing CRP info failed", ex, RequestHandlerCrpInfo.class);
      return null;
    }
  }
}
