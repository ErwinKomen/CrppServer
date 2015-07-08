/*
 * This software has been developed at the "Meertens Instituut"
 *    for the CLARIN project "CorpusStudio-WebApplication".
 *  The application is based on the "CorpusStudio" program written by Erwin R. Komen
 *    while working for the Radboud University Nijmegen.
 *  The program and the source can be freely used and re-distributed.
 */
package nl.ru.crpx.server.crp;

import java.io.File;
import nl.ru.crpx.project.CorpusResearchProject;
import nl.ru.crpx.search.SearchManager;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.requesthandlers.RequestHandler;
import nl.ru.crpx.tools.ErrHandle;
import nl.ru.util.FileUtil;

/**
 * CrpUser
 * Provide access for a user to a corpus research project
 * 
 * @author Erwin R. Komen
 */
public class CrpUser {
  // ========================= Constants =======================================
  static String sProjectBase = "/etc/project/"; // Base directory where user-spaces are stored
  static String sCorpusBase = "/etc/corpora/";  // Base directory where corpora are stored
  // ==================== Variables belonging to one CrpUser object ============
  CorpusResearchProject prjThis;  // The CRP to which the user has access
  String prjName;                 // The name of this CRP
  String userId;                  // The user that has access to this CRP
  // String lngIndex;                // The language for this project
  // File indexDir;                  // Pointer to the input directory
  ErrHandle errHandle;            // The error handler we are using
  SearchManager searchMan;        // Local pointer to the general search manager
  // ================ Initialisation of a new class element ===================
  public CrpUser(CrpPserver servlet, String sProjectName, /* String sLngIndex,  */
          String sUserId, ErrHandle errHandle) {
    // Set our error handler
    this.errHandle = errHandle;
    // Make sure errors are treated well
    try {
      // Get the correct input directory from the [indexName] part
      this.searchMan = servlet.getSearchManager();
      // this.indexDir = this.searchMan.getIndexDir(sLngIndex);
      this.prjName = sProjectName;
      this.userId = sUserId;
      // this.lngIndex = sLngIndex;
      // Load the project
      if (!initCrp(sProjectName /* , sLngIndex */)) {
        // errHandle.DoError("CrpUser: Could not load project [" + sProjectName + "] for language [" + sLngIndex + "]");
        errHandle.DoError("CrpUser: Could not load project [" + sProjectName + "]");
        return;
      }
      // Set the project type manager for the CRP
      this.prjThis.setPrjTypeManager(servlet.getPrjTypeManager());
    } catch (Exception ex) {
      errHandle.DoError("CrpUser: error while loading project [" + sProjectName + 
              "]", ex, CrpUser.class);
    }
  }
  
  /* ---------------------------------------------------------------------------
   Name: initCrp
   Goal: Initialize CRP-related parameters for this requesthandler
   Parameters:  @sProjectPath - HTTP request object
   History:
   7/nov/2014   ERK Created
   --------------------------------------------------------------------------- */
  public final boolean initCrp(String strProject /*, String sLngIndex */) {
    String sInputDir;   // Input directory for this project
    String sOutputDir;  // Output directory for this project
    String sQueryDir;   // Query directory for this project

    try {
      // Create room for a corpus research project
      CorpusResearchProject crpThis = new CorpusResearchProject(true);
      // Set output and query directory, depending on the user
      sOutputDir = FileUtil.nameNormalize(sProjectBase + "/" + this.userId + "/out");
      sQueryDir = FileUtil.nameNormalize(sProjectBase + "/" + this.userId + "/xq");
      // OLD: sInputDir = this.indexDir.getAbsolutePath();
      // Initialize the input directory to the general CorpusBase
      // Note: this value will get modified for a /exe call
      sInputDir = sCorpusBase;
      // Set the project path straight
      String sProjectPath = getCrpPath(strProject);
      // Load the project
      if (!crpThis.Load(sProjectPath, sInputDir, sOutputDir, sQueryDir)) {
        errHandle.DoError("Could not load project " + strProject);
        // Try to show the list of errors, if there is one
        String sMsg = crpThis.errHandle.getErrList().toString();
        errHandle.DoError("List of errors:\n" + sMsg);
        return false;
      }


      // Get my copy of the project
      this.prjThis = crpThis;
      // Set the search manager for this CRP
      crpThis.setSearchManager(this.searchMan);
      // Return positively
      return true;
    } catch (Exception ex) {
      errHandle.DoError("There's a problem initializing the CRP", ex, RequestHandler.class);
      // Return failure
      return false;
    }
  }
  /**
   * Given the name of a CRP, get its full path
   * 
   * @param sName
   * @return 
   */
  public String getCrpPath(String sName) {
    try {
      String sProjectPath = sName;
      
      // Set the project path straight
      if (!sProjectPath.contains("/")) {
        sProjectPath = FileUtil.nameNormalize(sProjectBase + "/" + this.userId + "/" + sProjectPath);
        if (!sProjectPath.contains(".")) {
          sProjectPath += ".crpx";
        }
      }
      // Return our findings
      return sProjectPath;
    } catch (Exception ex) {
      errHandle.DoError("Could not get CRP path", ex, RequestHandler.class);
      return "";
    }
  }
    
}
