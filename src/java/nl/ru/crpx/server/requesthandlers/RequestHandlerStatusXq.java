/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.ru.crpx.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.server.CrpPserver;
import static nl.ru.crpx.server.requesthandlers.RequestHandler.userId;
import org.apache.log4j.Logger;

/**
 * RequestHandlerStatusXq
 * Handle status information requests for "Xq" jobs
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerStatusXq  extends RequestHandler {
  @SuppressWarnings("hiding")
  private static final Logger logger = Logger.getLogger(RequestHandlerDebug.class);

  public RequestHandlerStatusXq(CrpPserver servlet, HttpServletRequest request, String indexName, String urlResource) {
    super(servlet, request, indexName, urlResource);
  }
  
  @Override
  public DataObject handle() {
    debug(logger, "REQ execute");
    // Prepare a status object to return
    DataObjectMapElement objStatus = new DataObjectMapElement();
    objStatus.put("code", "completed");
    objStatus.put("message", "The Java-part of the R-webservice works fine.");
    objStatus.put("userid", userId);
    // Prepare the total response: indexName + status object
    DataObjectMapElement response = new DataObjectMapElement();
    response.put("indexName", indexName);
    response.put("status", objStatus);
    return response;
  }  
}
