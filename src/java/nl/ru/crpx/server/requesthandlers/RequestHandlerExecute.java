package nl.ru.crpx.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.server.CrpPserver;
import static nl.ru.crpx.server.requesthandlers.RequestHandler.userId;
import org.apache.log4j.Logger;

/*
 * This software has been developed at the "Meertens Instituut"
 *   for the CLARIN project "CorpusStudio-WebApplication".
 * The application is based on the "CorpusStudio" program written by Erwin R. Komen
 *   while working for the Radboud University Nijmegen.
 * The program and the source can be freely used and re-distributed.
 */

/**
 *
 * @author Erwin R. Komen
 */
public class RequestHandlerExecute extends RequestHandler {
  @SuppressWarnings("hiding")
  private static final Logger logger = Logger.getLogger(RequestHandlerDebug.class);

  public RequestHandlerExecute(CrpPserver servlet, HttpServletRequest request, String indexName, String urlResource) {
    super(servlet, request, indexName, urlResource);
  }
  
  @Override
  public DataObject handle() {
    debug(logger, "REQ execute");
    // Get the project that should be loaded and executed
    // Get the argument we need to process
    sReqArgument = getReqString(request);
    logger.debug("XqJob query: " + sReqArgument);
    // Put the argument in the searchparameters
    searchParam.put("query", sReqArgument);
    // Try loading and initializing CRP
    if (!initCrp(sReqArgument)) {
      errHandle.DoError("Could not load the indicated project", null, RequestHandlerExecute.class);
      // Or should we return an error object??
      return null;
    }
    
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
