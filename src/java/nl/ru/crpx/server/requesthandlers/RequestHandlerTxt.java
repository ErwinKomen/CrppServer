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
import static nl.ru.crpx.server.requesthandlers.RequestHandler.errHandle;
import static nl.ru.crpx.server.requesthandlers.RequestHandler.getReqString;
import nl.ru.util.json.JSONObject;
import org.apache.log4j.Logger;


/**
 * RequestHandlerTxt
 * Give a list of all texts for a particular combination of Lng/Part
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerTxt extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerTxt.class);
  // =================== Local variables =======================================
  private CrpManager crpManager;

  // =================== Initialisation of this class ==========================
  public RequestHandlerTxt(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
  }
  
  @Override
  public DataObject handle() {
    String sFilter = "*";   // Default filter
    String sLng = "eng_hist";
    String sDir = "";
    String sExt = "";
    String sTextName = "";
    String[] arArgObl = {"userid","lng","ext","name"};
    int i;

    try {
      debug(logger, "REQ Txt");
      // Default values
      sReqArgument = getReqString(request);
      sCurrentUserId = "";
      // Get the JSON string arguments we need to process, e.g:
      //   {  "userid": "erkomen",          - User that requests this
      //      "lng":    "eng_hist",         - Directory within /etc/corpora
      //      "dir":    "lModE",            - Optional: corpus-part to look in
      //      "ext":    "psdx",             - Extension type: 'psdx' or 'folia'
      //      "name":   "abcdefgh[.psdx]"   - Name of the text, with or without extension
      //   }
      // Note: if no user is given, then we should give all users and all crp's
      logger.debug("Considering request /txt: " + sReqArgument);
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
      // Look for optional arguments
      if (jReq.has("dir")) sDir = jReq.getString("dir");
      // Get a list of all the databases available for the indicated user
      DataObject objContent = crpManager.getText( sLng, sDir, sExt, sTextName);
      if (objContent == null) 
        return DataObject.errorObject("INTERNAL_ERROR", "Txt failed on 'getText()' ");
      
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "See the list of texts in the [content] section");
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
