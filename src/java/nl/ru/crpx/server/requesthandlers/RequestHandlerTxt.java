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
import nl.ru.crpx.project.CorpusResearchProject;
import nl.ru.crpx.search.SearchManager;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import static nl.ru.crpx.server.requesthandlers.RequestHandler.errHandle;
import static nl.ru.crpx.server.requesthandlers.RequestHandler.getReqString;
import nl.ru.util.FileUtil;
import nl.ru.util.json.JSONObject;
import nl.ru.xmltools.XmlAccess;
import nl.ru.xmltools.XmlAccessFolia;
import nl.ru.xmltools.XmlAccessPsdx;
import java.util.logging.Logger;


/**
 * RequestHandlerTxt
 * Give a list of all texts for a particular combination of Lng/Part
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerTxt extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerTxt.class.getName());
  // =================== Local variables =======================================
  private CrpManager crpManager;
  private SearchManager srchManager;

  // =================== Initialisation of this class ==========================
  public RequestHandlerTxt(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
    this.srchManager = servlet.getSearchManager();
  }
  
  @Override
  public DataObject handle() {
    String sFilter = "*";             // Default filter
    String sLng = "eng_hist";
    String sDir = "";
    String sExt = "";
    String sTextName = "";
    String sActionType = "sentences"; // Default type is to fetch sentences of a text
    String sSentId = "";              // Optional parameter
    String sConstId = "";             // Optional parameter
    DataObject objContent = null;
    int iStart = 0;
    int iPageSize = 20;
    String[] arArgObl = {"userid","lng","ext","name"};
    int i;

    try {
      debug(logger, "REQ Txt");
      // Default values
      sReqArgument = getReqString(request);
      sCurrentUserId = "";
      // Get the JSON string arguments we need to process, e.g:
      //   {  "userid": "erkomen",          - User that requests this
      //      "lng":    "eng_hist",         - Directory within CrpInfo.sEtcCorpora 
      //      "dir":    "lModE",            - Optional: corpus-part to look in
      //      "ext":    "psdx",             - Extension type: 'psdx' or 'folia'
      //      "name":   "abcdefgh[.psdx]"   - Name of the text, with or without extension
      //      "type":   "syntax"            - Optional. If present: "grouping", "hits", "context", "msg", "syntax", "svg"
      //      "locs":   "fw.p.1.s.3"        - Optional. Sentence identifier
      //      "locw":   "fw.p.1.s.3.su.5"   - Optional. syntactic unit identifier
      //   }
      // Note: if no user is given, then we should give all users and all crp's
      debug(logger, "Considering request /txt: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      // Look for obligatory arguments
      for (i=0;i<arArgObl.length;i++) {
        if (!jReq.has(arArgObl[i])) return DataObject.errorObject("INTERNAL_ERROR", 
                      "Txt misses obligatory argument ["+arArgObl[i]+"]");
      }
      // Take the obligatory arguments
      sLng = jReq.getString("lng");
      sExt = jReq.getString("ext");
      sCurrentUserId = jReq.getString("userid");
      sTextName = jReq.getString("name");
      
      // Check on userid
      if (sCurrentUserId.isEmpty() || sCurrentUserId.equals("erkomen")) {
        return DataObject.errorObject("INTERNAL_ERROR", "not logged in");
      }
      
      // Look for optional arguments
      if (jReq.has("dir")) sDir = jReq.getString("dir");
      if (jReq.has("type")) sActionType = jReq.getString("type");
      if (jReq.has("locs")) sSentId = jReq.getString("locs");
      if (jReq.has("locw")) sConstId = jReq.getString("locw");
      
      // Special case: strip the '.gz' from the text name
      sTextName = sTextName.replace(".gz", "");
      
      // Action depends on the type
      switch(sActionType) {
        case "sentences":
          // Get iPageSize sentences from a text starting at iStart
          objContent = crpManager.getText( sLng, sDir, sExt, sTextName, iStart, iPageSize);
          if (objContent == null) {
            return DataObject.errorObject("INTERNAL_ERROR", "Txt failed on 'getText()' ");
          } else if (DataObject.isErrorObject(objContent)) {
            return objContent;
          }
          break;
        default:
          // Get iPageSize sentences from a text starting at iStart
          objContent = crpManager.getSentInfo( sLng, sDir, sExt, sTextName, sActionType, sSentId, sConstId);
          if (objContent == null) {
            return DataObject.errorObject("INTERNAL_ERROR", "Txt failed on 'getSentInfo()' ");
          } else if (DataObject.isErrorObject(objContent)) {
            return objContent;
          }
      }
      
      
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "See the text divided in line objects in the [content] section");
      objStatus.put("userid", userId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Providing a text failed", ex, RequestHandlerTxtList.class);
      return DataObject.errorObject("INTERNAL_ERROR", "Txt failed: " + ex.getMessage());
    }
  }
}
