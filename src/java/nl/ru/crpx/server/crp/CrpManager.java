/*
 * This software has been developed at the "Meertens Instituut"
 *    for the CLARIN project "CorpusStudio-WebApplication".
 *  The application is based on the "CorpusStudio" program written by Erwin R. Komen
 *    while working for the Radboud University Nijmegen.
 *  The program and the source can be freely used and re-distributed.
 */
package nl.ru.crpx.server.crp;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import nl.ru.crpx.project.CorpusResearchProject;
import nl.ru.crpx.server.CrpPserver;
import static nl.ru.crpx.server.crp.CrpUser.sProjectBase;
import nl.ru.crpx.tools.ErrHandle;
import nl.ru.util.FileUtil;
import nl.ru.util.json.JSONArray;
import nl.ru.util.json.JSONObject;

/**
 * CrpManager
 * Manage the CRP projects that are worked on by the different users
 * 
 * @author Erwin R. Komen
 */
public class CrpManager {
  // ================ Local variables ==========================================
  ErrHandle errHandle;            // The error handler we are using
  // ================ Static variables =========================================
  static List<CrpUser> loc_crpUserList; // List of CrpUser elements
  static int loc_id;                    // the Id of each CrpUser element
  static CrpPserver servlet;       // My own link to the search manager
  // ================ Class initialisation =====================================
  public CrpManager(CrpPserver servlet, ErrHandle errHandle) {
    // Initialize the id
    this.loc_id = -1;
    // Set the error handler
    this.errHandle = errHandle;
    // Set my own link to the search manager
    this.servlet = servlet;
    // Initialise the list
    loc_crpUserList = new ArrayList<>();
  }
  
  /**
   * getCrpUser
   * Either create a new CrpUser object, or get an already existing one
   * 
   * @param sProjectName
   * @param sLngIndex
   * @param sUserId
   * @return 
   */
  public CrpUser getCrpUser(String sProjectName, /* String sLngIndex, */
          String sUserId) {
    try {
      // Check if this combination already exists in the list
      for (CrpUser oCrpUser : loc_crpUserList) {
        // Check if this has the correct project name, language index and user id
        if (oCrpUser.prjName.equals(sProjectName) && oCrpUser.userId.equals(sUserId)
                /* && oCrpUser.lngIndex.equals(sLngIndex) */) {
          // Return this object
          return oCrpUser;
        } 
      } 
      // Getting here means that we need to create a new entry
      CrpUser oNewCrpUser = new CrpUser(servlet, sProjectName, /* sLngIndex, */ sUserId, errHandle);
      // Add this to the list
      loc_crpUserList.add(oNewCrpUser);
      errHandle.debug("adding CrpUser in getCrpUser: [" + sProjectName + 
              ", " + /* sLngIndex + ", " + */ sUserId + "]", CrpManager.class);
      // Return this newly created one
      return oNewCrpUser;
    } catch (Exception ex) {
      errHandle.DoError("Could not load or retrieve CRP", ex, CrpManager.class);
      return null;
    }
  }
  
  /**
   * getCrp
   * Retrieve (or load) the CRP belonging to the indicated project name, language and user
   * 
   * @param sProjectName
   * @param sLngIndex
   * @param sUserId
   * @return 
   */
  public CorpusResearchProject getCrp(String sProjectName, /* String sLngIndex,  */
          String sUserId) {
    try {
      CrpUser oCrpUser= getCrpUser(sProjectName, /* sLngIndex, */ sUserId);
      // Check what we get back
      if (oCrpUser == null)
        return null;
      else
        return oCrpUser.prjThis;
    } catch (Exception ex) {
      errHandle.DoError("Could not load or retrieve CRP", ex, CrpManager.class);
      return null;
    }
  }
  
  /**
   * getCrpList - get a list of the CRPs for the indicated user
   *              If no user is given: provide all users and all CRPs
   * 
   * @param sUserId
   * @return 
   */
  public JSONArray getCrpList(String sUserId) {
    String sUserPath;     // Where the users are stored
    List<String> lUsers;  // List of crpx
    int iPrj = 1;         // The project index
    
    try {
      // Create a list to reply
      JSONArray arBack = new JSONArray();
      
      // Get a path to the users
      sUserPath = FileUtil.nameNormalize(sProjectBase);
      if (sUserPath.isEmpty()) return arBack;
      // Initialise
      lUsers = new ArrayList<>();
      Path dir = Paths.get(sUserPath);
      // Get all the items inside "dir"
      try(DirectoryStream<Path> streamUser = Files.newDirectoryStream(dir)) {
        // Walk all these items
        for (Path pathUser : streamUser) {
          // Add the directory to the list of users
          lUsers.add(pathUser.toAbsolutePath().toString());
          // Get the user
          String sUser = pathUser.getFileName().toString();
          // Is this the user we are looking for?
          if (sUserId.isEmpty() || sUser.equals(sUserId)) {
            // Get all the CRP files in the user's directory
            DirectoryStream<Path> streamCrp = Files.newDirectoryStream(pathUser);
            for (Path pathCrp : streamCrp) {
              // Get the name of this crp
              String sCrp = pathCrp.getFileName().toString();
              // Okay, create a reply object
              JSONObject oOne = new JSONObject();
              oOne.put("userid", sUser);
              oOne.put("crp", sCrp);
              oOne.put("loaded", hasCrpUser(sCrp, sUser));
              // Include the object here
              arBack.put(oOne);
            }
          }
        }
      } catch(IOException ex) {
        errHandle.DoError("Could not get a list of CRP-User objects", ex, CrpManager.class);
      }
      // Return the array
      return arBack;
    } catch (Exception ex) {
      errHandle.DoError("Could not get a list of CRP-User objects", ex, CrpManager.class);
      return null;
    }
  }
  
  /**
   * hasCrpUser - does user @sUserId have the project named @sProjectName
   * 
   * @param sProjectFile
   * @param sUserId
   * @return 
   */
  public boolean hasCrpUser(String sProjectFile, String sUserId) {
    try {
      // Walk the list
      for (CrpUser oCrpUser : loc_crpUserList) {
        // Does this belong to the indecated user?
        if (sUserId.isEmpty() || sUserId.equals(oCrpUser.userId)) {
          // Is this the correct project?
          if (oCrpUser.prjName.equals(sProjectFile)) {
            // Yes, return positively
            return true;
          }
        }
      }
      // Failure
      return false;
    } catch (Exception ex) {
      errHandle.DoError("Could not check existence of Crp/User combination", ex, CrpManager.class);
      return false;
    }
  }
  
  /**
   * removeCrpUser
   * Remove the CrpUser object satisfying the indecated conditions
   * 
   * @param sProjectName
   * // @param sLngIndex
   * @param sUserId
   * @return 
   */
  public boolean removeCrpUser(String sProjectName, /* String sLngIndex, */ 
          String sUserId) {
    try {
      // Check if this combination already exists in the list
      for (CrpUser oCrpUser : loc_crpUserList) {
        // Check if this has the correct project name, language index and user id
        if (oCrpUser.prjName.equals(sProjectName) && oCrpUser.userId.equals(sUserId)
                /* && oCrpUser.lngIndex.equals(sLngIndex) */) {
          // We found it: now remove it
          errHandle.debug("removing CrpUser: [" + sProjectName + 
              ", " + /* sLngIndex + ", " + */  sUserId + "]", CrpManager.class);
          loc_crpUserList.remove(oCrpUser);
          // Return positively
          return true;
        } 
      } 
      // Return failure: we didn't find it
      return false;
    } catch (Exception ex) {
      errHandle.DoError("Could not remove CRP-User object", ex, CrpManager.class);
      return false;
    }
  }
}
