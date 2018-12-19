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
import nl.ru.crpx.project.CrpInfo;
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
  static String sProjectBase = CrpInfo.sEtcProject + "/"; // "/etc/project/"; // Base directory where user-spaces are stored
  static String sCorpusBase = CrpInfo.sEtcCorpora + "/";  // "/etc/corpora/";  // Base directory where corpora are stored
  // ==================== Variables belonging to one CrpUser object ============
  CorpusResearchProject prjThis;  // The CRP to which the user has access
  String prjName;                 // The name of this CRP
  String userId;                  // The user that has access to this CRP
  ErrHandle errHandle;            // The error handler we are using
  SearchManager searchMan;        // Local pointer to the general search manager
  // ================ Initialisation of a new class element ===================
  public CrpUser(CrpPserver servlet, String sProjectName, String sAction,
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
      if (!initCrp(sProjectName, sAction)) {
        errHandle.DoError("CrpUser: Could not "+sAction+" project [" + sProjectName + "]");
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
  public final boolean initCrp(String strProject, String sAction) {
    String sInputDir;   // Input directory for this project
    String sOutputDir;  // Output directory for this project
    String sQueryDir;   // Query directory for this project

    try {
      // Create room for a corpus research project
      CorpusResearchProject crpThis = new CorpusResearchProject(true);
      // Set output and query directory, depending on the user
      String sProjStart = (sProjectBase.endsWith("/")) ? sProjectBase : sProjectBase + "/";
      sOutputDir = FileUtil.nameNormalize(sProjStart + this.userId + "/out");
      sQueryDir = FileUtil.nameNormalize(sProjStart + this.userId + "/xq");
      // OLD: sInputDir = this.indexDir.getAbsolutePath();
      // Initialize the input directory to the general CorpusBase
      // Note: this value will get modified for a /exe call
      sInputDir = sCorpusBase;
      // Set the project path straight
      String sProjectPath = getCrpPath(strProject);
      // Init action: create or not?
      switch (sAction) {
        case "create":
          // Create the project
          if (!crpThis.Create(sProjectPath, sInputDir, sOutputDir, sQueryDir)) {
            errHandle.DoError("Could not create project " + strProject);
            // Try to show the list of errors, if there is one
            String sMsg = crpThis.errHandle.getErrList().toString();
            errHandle.DoError("List of errors:\n" + sMsg);
            return false;
          }
          break;
        case "load":
          // Check existence
          File fPrj = new File(sProjectPath);
          if (!fPrj.exists()) return false;
          // Load the project
          if (!crpThis.Load(sProjectPath, sInputDir, sOutputDir, sQueryDir)) {
            errHandle.DoError("Could not load project " + strProject);
            // Try to show the list of errors, if there is one
            String sMsg = crpThis.errHandle.getErrList().toString();
            errHandle.DoError("List of errors:\n" + sMsg);
            return false;
          }
          break;
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
        String sProjStart = (sProjectBase.endsWith("/")) ? sProjectBase :
                sProjectBase + "/";
        sProjectPath = FileUtil.nameNormalize(sProjStart + this.userId + "/" + sProjectPath);
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
