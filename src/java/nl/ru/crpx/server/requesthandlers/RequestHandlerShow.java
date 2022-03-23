package nl.ru.crpx.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.server.CrpPserver;
import java.util.logging.Logger;

/*
 * This software has been developed at the "Meertens Instituut"
 *   for the CLARIN project "CorpusStudio-WebApplication".
 * The application is based on the "CorpusStudio" program written by Erwin R. Komen
 *   while working for the Radboud University Nijmegen.
 * The program and the source can be freely used and re-distributed.
 */

/**
 * RequestHandlerShow
 * 
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerShow extends RequestHandler {
  @SuppressWarnings("hiding")
  private static final Logger logger = Logger.getLogger(RequestHandlerDebug.class.getName());

  public RequestHandlerShow(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
  }
  
  @Override
  public DataObject handle() {
    debug(logger, "REQ execute");
    // Prepare a status object to return
    DataObjectMapElement objStatus = new DataObjectMapElement();
    objStatus.put("code", "completed");
    objStatus.put("message", "The Java-part of the CRPP service works fine.");
    objStatus.put("userid", userId);
    // Prepare the total response: indexName + status object
    DataObjectMapElement response = new DataObjectMapElement();
    response.put("indexName", indexName);
    response.put("status", objStatus);
    return response;
  }  
}
