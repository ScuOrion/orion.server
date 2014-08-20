/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.metastore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreMigration;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreUtil;
import org.eclipse.orion.internal.server.core.metastore.SimpleUserPasswordUtil;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Tests to ensure that the older versions of the Metadata storage are automatically updated to
 * the latest version.
 * 
 * @author Anthony Hunter
 */
public class SimpleMetaStoreLiveMigrationTests extends FileSystemTest {

	/**
	 * type-safe value for an empty list.
	 */
	protected static final List<String> EMPTY_LIST = Collections.emptyList();

	@BeforeClass
	public static void initializeRootFileStorePrefixLocation() {
		initializeWorkspaceLocation();
	}

	protected JSONObject createProjectJson(int version, String userId, String workspaceName, String projectName, File contentLocation) throws Exception {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(SimpleMetaStore.ORION_VERSION, version);
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		jsonObject.put("WorkspaceId", workspaceId);
		String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectName);
		jsonObject.put("UniqueId", projectId);
		jsonObject.put("FullName", projectName);
		String encodedContentLocation = SimpleMetaStoreUtil.encodeProjectContentLocation(contentLocation.toURI().toString());
		jsonObject.put("ContentLocation", encodedContentLocation);
		JSONObject properties = new JSONObject();
		jsonObject.put("Properties", properties);
		return jsonObject;
	}

	protected void createProjectMetaData(int version, JSONObject newProjectJSON, String userId, String workspaceName, String projectName) throws Exception {
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		assertNotNull(workspaceId);
		assertTrue(SimpleMetaStoreUtil.isMetaUserFolder(getWorkspaceRoot(), userId));
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), userId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		assertNotNull(encodedWorkspaceName);
		assertTrue(SimpleMetaStoreUtil.isMetaFolder(userMetaFolder, encodedWorkspaceName));
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
		String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectName);
		assertFalse(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, projectId));
		assertFalse(SimpleMetaStoreUtil.isMetaFolder(workspaceMetaFolder, projectId));
		assertTrue(SimpleMetaStoreUtil.createMetaFolder(workspaceMetaFolder, projectId));
		File projectFolder = SimpleMetaStoreUtil.readMetaFolder(workspaceMetaFolder, projectId);
		assertNotNull(projectFolder);
		assertTrue(projectFolder.exists());
		assertTrue(projectFolder.isDirectory());
		File projectMetaFile;
		if (version == SimpleMetaStoreMigration.VERSION4) {
			// the project metadata is saved in a file in the workspace folder.
			assertTrue(SimpleMetaStoreUtil.createMetaFile(workspaceMetaFolder, projectId, newProjectJSON));
			projectMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(workspaceMetaFolder, projectId);
		} else {
			// the project metadata is saved in a file in the user folder.
			assertTrue(SimpleMetaStoreUtil.createMetaFile(userMetaFolder, projectId, newProjectJSON));
			projectMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, projectId);
		}
		assertTrue("Could not create file " + projectMetaFile.toString(), projectMetaFile.exists() && projectMetaFile.isFile());

		// Update the JUnit base variables
		testProjectBaseLocation = "/" + workspaceId + '/' + projectName;
		testProjectLocalFileLocation = "/" + projectId;
	}

	/**
	 * Creates the sample directory in the local directory using the IFileStore API.
	 * @param directoryPath
	 * @param fileName
	 * @throws Exception
	 */
	protected String createSampleDirectory() throws Exception {
		assertFalse("Test Project Base Location should not be the empty string, user or workspace or project failure", "".equals(getTestBaseResourceURILocation()));
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);
		return directoryPath;
	}

	/**
	 * Creates the sample file in the local directory using the IFileStore API.
	 * @param directoryPath
	 * @param fileName
	 * @throws Exception
	 */
	protected String createSampleFile(String directoryPath) throws Exception {
		String fileName = "sampleFile" + System.currentTimeMillis() + ".txt";
		String fileContent = fileName;
		createFile(directoryPath + "/" + fileName, fileContent);
		return fileName;
	}

	/**
	 * Create the content for a user.json file to be saved to the disk
	 * @param version The SimpleMetaStore version.
	 * @param userId The userId
	 * @param workspaceIds A list of workspace Ids.
	 * @return The JSON object.
	 * @throws Exception
	 */
	protected JSONObject createUserJson(int version, String userId, List<String> workspaceIds) throws Exception {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(SimpleMetaStore.ORION_VERSION, version);
		jsonObject.put("UniqueId", userId);
		jsonObject.put("UserName", userId);
		jsonObject.put("FullName", userId);
		String password = SimpleUserPasswordUtil.encryptPassword(userId);
		jsonObject.put("password", password);
		JSONObject properties = new JSONObject();
		properties.put("UserRightsVersion", "3");
		JSONArray userRights = new JSONArray();
		JSONObject userRight = new JSONObject();
		userRight.put("Method", 15);
		String usersRight = "/users/";
		userRight.put("Uri", usersRight.concat(userId));
		userRights.put(userRight);
		JSONArray workspaceIdsJson = new JSONArray();
		for (String workspaceId : workspaceIds) {
			workspaceIdsJson.put(workspaceId);

			userRight = new JSONObject();
			userRight.put("Method", 15);
			String workspaceRight = "/workspace/";
			userRight.put("Uri", workspaceRight.concat(workspaceId));
			userRights.put(userRight);

			userRight = new JSONObject();
			userRight.put("Method", 15);
			userRight.put("Uri", workspaceRight.concat(workspaceId).concat("/*"));
			userRights.put(userRight);

			userRight = new JSONObject();
			userRight.put("Method", 15);
			String fileRight = "/file/";
			userRight.put("Uri", fileRight.concat(workspaceId));
			userRights.put(userRight);

			userRight = new JSONObject();
			userRight.put("Method", 15);
			userRight.put("Uri", fileRight.concat(workspaceId).concat("/*"));
			userRights.put(userRight);
		}
		jsonObject.put("WorkspaceIds", workspaceIdsJson);
		properties.put("UserRights", userRights);
		jsonObject.put("Properties", properties);
		return jsonObject;
	}

	protected void createUserMetaData(JSONObject newUserJSON, String userId) throws CoreException {
		SimpleMetaStoreUtil.createMetaUserFolder(getWorkspaceRoot(), userId);
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), userId);
		assertTrue("Could not create directory " + userMetaFolder.toString(), userMetaFolder.exists() && userMetaFolder.isDirectory());
		assertFalse(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, SimpleMetaStore.USER));
		SimpleMetaStoreUtil.createMetaFile(userMetaFolder, SimpleMetaStore.USER, newUserJSON);
		File userMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, SimpleMetaStore.USER);
		assertTrue("Could not create file " + userMetaFile.toString(), userMetaFile.exists() && userMetaFile.isFile());
	}

	/**
	 * Create the content for a user.json file to be saved to the disk
	 * @param version The SimpleMetaStore version.
	 * @param userId The userId
	 * @param workspaceIds A list of workspace Ids.
	 * @return The JSON object.
	 * @throws Exception
	 */
	protected JSONObject createWorkspaceJson(int version, String userId, String workspaceName, List<String> projectNames) throws Exception {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(SimpleMetaStore.ORION_VERSION, version);
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		jsonObject.put("UniqueId", workspaceId);
		jsonObject.put("UserId", userId);
		jsonObject.put("FullName", workspaceName);
		JSONArray projectNamesJson = new JSONArray();
		for (String projectName : projectNames) {
			projectNamesJson.put(projectName);
		}
		jsonObject.put("ProjectNames", projectNamesJson);
		JSONObject properties = new JSONObject();
		jsonObject.put("Properties", properties);
		return jsonObject;
	}

	protected void createWorkspaceMetaData(int version, JSONObject newWorkspaceJSON, String userId, String workspaceName) throws CoreException {
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		assertNotNull(workspaceId);
		assertTrue(SimpleMetaStoreUtil.isMetaUserFolder(getWorkspaceRoot(), userId));
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), userId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		assertNotNull(encodedWorkspaceName);
		assertTrue(SimpleMetaStoreUtil.createMetaFolder(userMetaFolder, encodedWorkspaceName));
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
		assertNotNull(workspaceMetaFolder);
		File workspaceMetaFile;
		if (version == SimpleMetaStoreMigration.VERSION4) {
			// the workspace metadata is saved in a file named workspace.json in the workspace folder.
			assertFalse(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, SimpleMetaStore.WORKSPACE));
			assertTrue(SimpleMetaStoreUtil.createMetaFile(workspaceMetaFolder, SimpleMetaStore.WORKSPACE, newWorkspaceJSON));
			workspaceMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(workspaceMetaFolder, SimpleMetaStore.WORKSPACE);
			assertNotNull(workspaceMetaFile);
		} else {
			// the workspace metadata is saved in a file named {workspaceid}.json in the user folder.
			assertFalse(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, workspaceId));
			assertTrue(SimpleMetaStoreUtil.createMetaFile(userMetaFolder, workspaceId, newWorkspaceJSON));
			workspaceMetaFile = SimpleMetaStoreUtil.retrieveMetaFile(userMetaFolder, workspaceId);
			assertNotNull(workspaceMetaFile);
		}
		assertTrue("Could not create file " + workspaceMetaFile.toString(), workspaceMetaFile.exists() && workspaceMetaFile.isFile());
	}

	protected File getProjectDefaultContentLocation(String userId, String workspaceName, String projectName) throws CoreException {
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), userId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		File workspaceMetaFolder = SimpleMetaStoreUtil.retrieveMetaFolder(userMetaFolder, encodedWorkspaceName);
		assertEquals(workspaceMetaFolder.getParent(), userMetaFolder.toString());
		String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectName);
		File projectFolder = SimpleMetaStoreUtil.retrieveMetaFolder(workspaceMetaFolder, projectId);
		assertNotNull(projectFolder);
		assertFalse(projectFolder.exists());
		assertEquals(projectFolder.getParent(), workspaceMetaFolder.toString());
		return projectFolder;
	}

	@Before
	public void setUp() throws Exception {
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
	}

	/**
	 * A user named growth8 with one workspace with a non standard name and two projects in SimpleMetaStore version 4 format.
	 * Matches a user on an internal server.
	 * @throws Exception
	 */
	@Test
	public void testUserGrowth8WithOneWorkspaceTwoProjectsVersionFour() throws Exception {
		testUserId = "growth8";
		testUserLogin = testUserId;
		testUserPassword = testUserId;
		String workspaceName = "New Sandbox";
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, workspaceName);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);
		List<String> projectNames = new ArrayList<String>();
		projectNames.add("growth8 | growth3");
		projectNames.add("growth8 | simpleProject");

		// create metadata on disk
		JSONObject newUserJSON = createUserJson(SimpleMetaStoreMigration.VERSION4, testUserId, workspaceIds);
		// tweak the default to match the internal server's metadata.
		newUserJSON.put("FullName", "Unnamed User");
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(SimpleMetaStoreMigration.VERSION4, testUserId, workspaceName, projectNames);
		createWorkspaceMetaData(SimpleMetaStoreMigration.VERSION4, newWorkspaceJSON, testUserId, workspaceName);
		File defaultContentLocation = getProjectDefaultContentLocation(testUserId, workspaceName, projectNames.get(0));
		JSONObject newProjectJSON = createProjectJson(SimpleMetaStoreMigration.VERSION4, testUserId, workspaceName, projectNames.get(0), defaultContentLocation);
		createProjectMetaData(SimpleMetaStoreMigration.VERSION4, newProjectJSON, testUserId, workspaceName, projectNames.get(0));
		defaultContentLocation = getProjectDefaultContentLocation(testUserId, workspaceName, projectNames.get(1));
		newProjectJSON = createProjectJson(SimpleMetaStoreMigration.VERSION4, testUserId, workspaceName, projectNames.get(1), defaultContentLocation);
		createProjectMetaData(SimpleMetaStoreMigration.VERSION4, newProjectJSON, testUserId, workspaceName, projectNames.get(1));

		// create the sample content
		String directoryPath = createSampleDirectory();
		String fileName = createSampleFile(directoryPath);

		// verify web requests
		verifyWorkspaceRequest(workspaceIds);
		verifyProjectRequest(testUserId, workspaceName, projectNames.get(0));
		verifyProjectRequest(testUserId, workspaceName, projectNames.get(1));
		verifySampleFileContents(directoryPath, fileName);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, workspaceName, projectNames);
		verifyProjectMetaData(testUserId, workspaceName, projectNames.get(0));
		verifyProjectMetaData(testUserId, workspaceName, projectNames.get(1));
	}

	/**
	 * A user with no workspaces.
	 * @param version The SimpleMetaStore version 
	 * @throws Exception
	 */
	protected void testUserWithNoWorkspaces(int version) throws Exception {
		// create metadata on disk
		testUserId = testName.getMethodName();
		JSONObject newUserJSON = createUserJson(version, testUserId, EMPTY_LIST);
		createUserMetaData(newUserJSON, testUserId);

		// verify web requests
		verifyWorkspaceRequest(EMPTY_LIST);

		// verify metadata on disk
		verifyUserMetaData(testUserId, EMPTY_LIST);
	}

	/**
	 * A user with no workspaces created by the tests framework.
	 * @throws Exception
	 */
	@Test
	public void testUserWithNoWorkspacesUsingFramework() throws Exception {
		// perform the basic step from the parent abstract test class.
		setUpAuthorization();

		// verify the web request
		verifyWorkspaceRequest(EMPTY_LIST);

		// verify metadata on disk
		verifyUserMetaData(testUserId, EMPTY_LIST);
	}

	/**
	 * A user with no workspaces in SimpleMetaStore version 4 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithNoWorkspacesVersionFour() throws Exception {
		testUserWithNoWorkspaces(SimpleMetaStoreMigration.VERSION4);
	}

	/**
	 * A user with no workspaces in SimpleMetaStore version 7 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithNoWorkspacesVersionSeven() throws Exception {
		testUserWithNoWorkspaces(SimpleMetaStore.VERSION);
	}

	/**
	 * A user with no workspaces in SimpleMetaStore version 6 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithNoWorkspacesVersionSix() throws Exception {
		testUserWithNoWorkspaces(SimpleMetaStoreMigration.VERSION6);
	}

	/**
	 * A user with one workspace and no projects.
	 * @param version The SimpleMetaStore version 
	 * @throws Exception
	 */
	protected void testUserWithOneWorkspaceNoProjects(int version) throws Exception {
		testUserId = testName.getMethodName();
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);

		// create metadata on disk
		testUserId = testName.getMethodName();
		JSONObject newUserJSON = createUserJson(version, testUserId, workspaceIds);
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(version, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, EMPTY_LIST);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);

		// verify web requests
		verifyWorkspaceRequest(workspaceIds);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, EMPTY_LIST);
	}

	/**
	 * A user with one workspace and no projects created by the tests framework.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceNoProjectsUsingFramework() throws Exception {
		// perform the basic steps from the parent abstract test class.
		setUpAuthorization();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);

		// verify web request
		verifyWorkspaceRequest(workspaceIds);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, EMPTY_LIST);
	}

	/**
	* A user with one workspace and no projects in SimpleMetaStore version 4 format.
	* @throws Exception
	*/
	@Test
	public void testUserWithOneWorkspaceNoProjectsVersionFour() throws Exception {
		testUserWithOneWorkspaceNoProjects(SimpleMetaStoreMigration.VERSION4);
	}

	/**
	 * A user with one workspace and no projects in SimpleMetaStore version 7 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceNoProjectsVersionSeven() throws Exception {
		testUserWithOneWorkspaceNoProjects(SimpleMetaStore.VERSION);
	}

	/**
	 * A user with one workspace and no projects in SimpleMetaStore version 6 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceNoProjectsVersionSix() throws Exception {
		testUserWithOneWorkspaceNoProjects(SimpleMetaStoreMigration.VERSION6);
	}

	/**
	 * A user with one workspace and one project. Additionally confirm the workspace is given the default workspace name.
	 * @param version The SimpleMetaStore version 
	 * @throws Exception
	 */
	protected void testUserWithOneWorkspaceOneProject(int version, String workspaceName) throws Exception {
		testUserId = testName.getMethodName();
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, workspaceName);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);
		List<String> projectNames = new ArrayList<String>();
		projectNames.add(testName.getMethodName().concat("Project"));

		// create metadata on disk
		JSONObject newUserJSON = createUserJson(version, testUserId, workspaceIds);
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(version, testUserId, workspaceName, projectNames);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, workspaceName);
		File defaultContentLocation = getProjectDefaultContentLocation(testUserId, workspaceName, projectNames.get(0));
		JSONObject newProjectJSON = createProjectJson(version, testUserId, workspaceName, projectNames.get(0), defaultContentLocation);
		createProjectMetaData(version, newProjectJSON, testUserId, workspaceName, projectNames.get(0));

		// create the sample content
		String directoryPath = createSampleDirectory();
		String fileName = createSampleFile(directoryPath);

		// verify web requests
		verifyWorkspaceRequest(workspaceIds);
		verifyProjectRequest(testUserId, workspaceName, projectNames.get(0));
		verifySampleFileContents(directoryPath, fileName);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, workspaceName, projectNames);
		verifyProjectMetaData(testUserId, workspaceName, projectNames.get(0));
	}

	/**
	 * A user with one workspace and one project created by the tests framework.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceOneProjectUsingFramework() throws Exception {
		// perform the basic steps from the parent abstract test class.
		setUpAuthorization();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);
		createTestProject(testName.getMethodName());
		List<String> projectNames = new ArrayList<String>();
		projectNames.add(testName.getMethodName().concat("Project"));

		// create the sample content
		String directoryPath = createSampleDirectory();
		String fileName = createSampleFile(directoryPath);

		// verify web requests
		verifyWorkspaceRequest(workspaceIds);
		verifyProjectRequest(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		verifySampleFileContents(directoryPath, fileName);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames);
		verifyProjectMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
	}

	/**
	* A user with one workspace and one project in SimpleMetaStore version 4 format.
	* @throws Exception
	*/
	@Test
	public void testUserWithOneWorkspaceOneProjectVersionFour() throws Exception {
		testUserWithOneWorkspaceOneProject(SimpleMetaStoreMigration.VERSION4, "Work SandBox");
	}

	/**
	 * A user with one workspace and one project in SimpleMetaStore version 7 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceOneProjectVersionSeven() throws Exception {
		testUserWithOneWorkspaceOneProject(SimpleMetaStore.VERSION, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
	}

	/**
	 * A user with one workspace and one project in SimpleMetaStore version 6 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceOneProjectVersionSix() throws Exception {
		testUserWithOneWorkspaceOneProject(SimpleMetaStoreMigration.VERSION6, "Sandbox");
	}

	/**
	 * A user with one workspace and two projects.
	 * @param version The SimpleMetaStore version 
	 * @throws Exception
	 */
	protected void testUserWithOneWorkspaceTwoProjects(int version) throws Exception {
		testUserId = testName.getMethodName();
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);
		List<String> projectNames = new ArrayList<String>();
		projectNames.add(testName.getMethodName().concat("Project"));
		projectNames.add("Second Project");

		// create metadata on disk
		JSONObject newUserJSON = createUserJson(version, testUserId, workspaceIds);
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(version, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		File defaultContentLocation = getProjectDefaultContentLocation(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		JSONObject newProjectJSON = createProjectJson(version, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0), defaultContentLocation);
		createProjectMetaData(version, newProjectJSON, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		defaultContentLocation = getProjectDefaultContentLocation(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(1));
		newProjectJSON = createProjectJson(version, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(1), defaultContentLocation);
		createProjectMetaData(version, newProjectJSON, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(1));

		// create the sample content
		String directoryPath = createSampleDirectory();
		String fileName = createSampleFile(directoryPath);

		// verify web requests
		verifyWorkspaceRequest(workspaceIds);
		verifyProjectRequest(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		verifyProjectRequest(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(1));
		verifySampleFileContents(directoryPath, fileName);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames);
		verifyProjectMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		verifyProjectMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(1));
	}

	/**
	* A user with one workspace and two projects in SimpleMetaStore version 4 format.
	* @throws Exception
	*/
	@Test
	public void testUserWithOneWorkspaceTwoProjectsVersionFour() throws Exception {
		testUserWithOneWorkspaceTwoProjects(SimpleMetaStoreMigration.VERSION4);
	}

	/**
	 * A user with one workspace and two projects in SimpleMetaStore version 7 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceTwoProjectsVersionSeven() throws Exception {
		testUserWithOneWorkspaceTwoProjects(SimpleMetaStore.VERSION);
	}

	/**
	 * A user with one workspace and two projects in SimpleMetaStore version 6 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithOneWorkspaceTwoProjectsVersionSix() throws Exception {
		testUserWithOneWorkspaceTwoProjects(SimpleMetaStoreMigration.VERSION6);
	}

	/**
	 * A user with two workspaces and two projects.
	 * @param version The SimpleMetaStore version 
	 * @throws Exception
	 */
	protected void testUserWithTwoWorkspacesTwoProjects(int version) throws Exception {
		testUserId = testName.getMethodName();
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		List<String> workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);
		List<String> projectNames = new ArrayList<String>();
		projectNames.add(testName.getMethodName().concat("Project"));
		String secondWorkspaceName = "Second Workspace";
		String secondWorkspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(testUserId, secondWorkspaceName);
		workspaceIds.add(secondWorkspaceId);
		List<String> secondProjectNames = new ArrayList<String>();
		secondProjectNames.add("Second Project");

		// create metadata on disk
		JSONObject newUserJSON = createUserJson(version, testUserId, workspaceIds);
		createUserMetaData(newUserJSON, testUserId);
		JSONObject newWorkspaceJSON = createWorkspaceJson(version, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		newWorkspaceJSON = createWorkspaceJson(version, testUserId, secondWorkspaceName, secondProjectNames);
		createWorkspaceMetaData(version, newWorkspaceJSON, testUserId, secondWorkspaceName);

		File defaultContentLocation = getProjectDefaultContentLocation(testUserId, secondWorkspaceName, secondProjectNames.get(0));
		JSONObject newProjectJSON = createProjectJson(version, testUserId, secondWorkspaceName, secondProjectNames.get(0), defaultContentLocation);
		createProjectMetaData(version, newProjectJSON, testUserId, secondWorkspaceName, secondProjectNames.get(0));

		defaultContentLocation = getProjectDefaultContentLocation(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		newProjectJSON = createProjectJson(version, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0), defaultContentLocation);
		createProjectMetaData(version, newProjectJSON, testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));

		// create the sample content
		String directoryPath = createSampleDirectory();
		String fileName = createSampleFile(directoryPath);

		// Fix the workspace ids now that the migration has run and there is only one wrkspace
		workspaceIds = new ArrayList<String>();
		workspaceIds.add(workspaceId);

		// verify web requests
		verifyWorkspaceRequest(workspaceIds);
		verifyProjectRequest(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		verifyProjectRequest(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, secondProjectNames.get(0));
		verifySampleFileContents(directoryPath, fileName);

		// verify metadata on disk
		verifyUserMetaData(testUserId, workspaceIds);
		verifyWorkspaceMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames);
		verifyWorkspaceMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, secondProjectNames);
		verifyProjectMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, projectNames.get(0));
		verifyProjectMetaData(testUserId, SimpleMetaStore.DEFAULT_WORKSPACE_NAME, secondProjectNames.get(0));
	}

	/**
	* A user with two workspaces and two projects in SimpleMetaStore version 4 format.
	* @throws Exception
	*/
	@Test
	public void testUserWithTwoWorkspacesTwoProjectsVersionFour() throws Exception {
		testUserWithTwoWorkspacesTwoProjects(SimpleMetaStoreMigration.VERSION4);
	}

	/**
	 * A user with two workspaces and two projects in SimpleMetaStore version 6 format.
	 * @throws Exception
	 */
	@Test
	public void testUserWithTwoWorkspacesTwoProjectsVersionSix() throws Exception {
		testUserWithTwoWorkspacesTwoProjects(SimpleMetaStoreMigration.VERSION6);
	}

	protected void verifyProjectJson(JSONObject jsonObject, String userId, String workspaceId, String projectName, File contentLocation) throws Exception {
		assertTrue(jsonObject.has(SimpleMetaStore.ORION_VERSION));
		assertEquals("OrionVersion is incorrect", SimpleMetaStore.VERSION, jsonObject.getInt(SimpleMetaStore.ORION_VERSION));
		assertTrue(jsonObject.has("UniqueId"));
		assertEquals(projectName, jsonObject.getString("UniqueId"));
		assertTrue(jsonObject.has("WorkspaceId"));
		assertEquals(workspaceId, jsonObject.getString("WorkspaceId"));
		assertTrue(jsonObject.has("FullName"));
		assertEquals(projectName, jsonObject.getString("FullName"));
		assertTrue(jsonObject.has("Properties"));
		assertTrue(jsonObject.has("ContentLocation"));
		String contentLocationFromJson = jsonObject.getString("ContentLocation");
		assertTrue(contentLocationFromJson.startsWith(SimpleMetaStoreUtil.SERVERWORKSPACE));
		String decodedContentLocationFromJson = SimpleMetaStoreUtil.decodeProjectContentLocation(contentLocationFromJson);
		URI contentLocationFileFromJson = new URI(decodedContentLocationFromJson);
		assertEquals(SimpleMetaStoreUtil.FILE_SCHEMA, contentLocationFileFromJson.getScheme());
		assertEquals(contentLocation.toString(), contentLocationFileFromJson.getPath());
	}

	protected void verifyProjectMetaData(String userId, String workspaceName, String projectName) throws Exception {
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		assertNotNull(workspaceId);
		assertTrue(SimpleMetaStoreUtil.isMetaUserFolder(getWorkspaceRoot(), userId));
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), userId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		assertNotNull(encodedWorkspaceName);
		assertTrue(SimpleMetaStoreUtil.isMetaFolder(userMetaFolder, encodedWorkspaceName));
		File workspaceMetaFolder = SimpleMetaStoreUtil.readMetaFolder(userMetaFolder, encodedWorkspaceName);
		String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectName);
		assertTrue(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, projectId));
		assertTrue(SimpleMetaStoreUtil.isMetaFolder(workspaceMetaFolder, projectId));
		File projectFolder = SimpleMetaStoreUtil.readMetaFolder(workspaceMetaFolder, projectId);
		assertTrue(projectFolder.exists());
		assertTrue(projectFolder.isDirectory());
		JSONObject projectJson = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, projectId);
		verifyProjectJson(projectJson, userId, workspaceId, projectName, projectFolder);
	}

	/**
	 * Verifies the test user has the specified number of workspaces.
	 * @param workspaces Number of workspaces.
	 */
	protected void verifyProjectRequest(String userId, String workspaceName, String projectName) throws Exception {
		//now get the project metadata
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		String projectId = SimpleMetaStoreUtil.encodeProjectIdFromProjectName(projectName);
		String encodedProjectId = URLEncoder.encode(projectId, "UTF-8").replace("+", "%20");
		String projectLocation = "workspace/" + workspaceId + "/project/" + encodedProjectId;
		WebRequest request = new GetMethodWebRequest(SERVER_LOCATION + projectLocation);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("application/json", response.getContentType());
		JSONObject responseObject = new JSONObject(response.getText());
		String sourceContentLocation = responseObject.optString(ProtocolConstants.KEY_CONTENT_LOCATION);
		assertNotNull(sourceContentLocation);
		assertEquals(projectName, responseObject.optString(ProtocolConstants.KEY_NAME));
	}

	/**
	 * Verifies the sample content using the remote Orion API. Verifies the user has been
	 * migrated successfully and all is good with the account.
	 * @param directoryPath
	 * @param fileName
	 * @throws Exception
	 */
	protected void verifySampleFileContents(String directoryPath, String fileName) throws Exception {
		String location = directoryPath + "/" + fileName;
		String path = new Path(FILE_SERVLET_LOCATION).append(getTestBaseResourceURILocation()).append(location).toString();
		String requestURI = URIUtil.fromString(SERVER_LOCATION + path).toString();
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("Invalid file content", fileName, response.getText());
	}

	protected void verifyUserJson(JSONObject jsonObject, String userId, List<String> workspaceIds) throws Exception {
		assertTrue(jsonObject.has(SimpleMetaStore.ORION_VERSION));
		assertEquals("OrionVersion is incorrect", SimpleMetaStore.VERSION, jsonObject.getInt(SimpleMetaStore.ORION_VERSION));
		assertTrue(jsonObject.has("UniqueId"));
		assertEquals(userId, jsonObject.getString("UniqueId"));
		assertTrue(jsonObject.has("UserName"));
		assertEquals(userId, jsonObject.getString("UserName"));
		assertTrue(jsonObject.has("FullName"));
		assertTrue(jsonObject.has("WorkspaceIds"));
		JSONArray workspaceIdsFromJson = jsonObject.getJSONArray("WorkspaceIds");
		assertNotNull(workspaceIdsFromJson);
		for (String workspaceId : workspaceIds) {
			verifyValueExistsInJsonArray(workspaceIdsFromJson, workspaceId);
		}
		assertTrue(jsonObject.has("password"));
		assertTrue(jsonObject.has("Properties"));
		JSONObject properties = jsonObject.getJSONObject("Properties");
		assertTrue(properties.has("UserRightsVersion"));
		assertTrue(properties.has("UserRights"));
	}

	protected void verifyUserMetaData(String userId, List<String> workspaceIds) throws Exception {
		assertTrue(SimpleMetaStoreUtil.isMetaUserFolder(getWorkspaceRoot(), userId));
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), userId);
		assertTrue(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, SimpleMetaStore.USER));
		JSONObject userJSON = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, SimpleMetaStore.USER);
		verifyUserJson(userJSON, userId, workspaceIds);
	}

	protected void verifyValueExistsInJsonArray(JSONArray jsonArray, String value) throws JSONException {
		for (int i = 0; i < jsonArray.length(); i++) {
			String jsonValue = jsonArray.getString(i);
			if (value.equals(jsonValue)) {
				return;
			}
		}
		fail("Value \"" + value + "\" does not exist in JSONArray " + jsonArray.toString());
	}

	protected void verifyWorkspaceJson(JSONObject jsonObject, String userId, String workspaceId, String workspaceName, List<String> projectNames) throws Exception {
		assertTrue(jsonObject.has(SimpleMetaStore.ORION_VERSION));
		assertEquals("OrionVersion is incorrect", SimpleMetaStore.VERSION, jsonObject.getInt(SimpleMetaStore.ORION_VERSION));
		assertTrue(jsonObject.has("UniqueId"));
		assertEquals(workspaceId, jsonObject.getString("UniqueId"));
		assertTrue(jsonObject.has("UserId"));
		assertEquals(userId, jsonObject.getString("UserId"));
		assertTrue(jsonObject.has("FullName"));
		assertEquals(workspaceName, jsonObject.getString("FullName"));
		assertTrue(jsonObject.has("ProjectNames"));
		JSONArray projectNamesJson = jsonObject.getJSONArray("ProjectNames");
		for (String projectName : projectNames) {
			projectNamesJson.put(projectName);
		}
		assertNotNull(projectNamesJson);
		assertTrue(jsonObject.has("Properties"));
	}

	protected void verifyWorkspaceMetaData(String userId, String workspaceName, List<String> projectNames) throws Exception {
		String workspaceId = SimpleMetaStoreUtil.encodeWorkspaceId(userId, workspaceName);
		assertNotNull(workspaceId);
		assertTrue(SimpleMetaStoreUtil.isMetaUserFolder(getWorkspaceRoot(), userId));
		File userMetaFolder = SimpleMetaStoreUtil.readMetaUserFolder(getWorkspaceRoot(), userId);
		String encodedWorkspaceName = SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(workspaceId);
		assertNotNull(encodedWorkspaceName);
		assertTrue(SimpleMetaStoreUtil.isMetaFolder(userMetaFolder, encodedWorkspaceName));
		assertTrue(SimpleMetaStoreUtil.isMetaFile(userMetaFolder, workspaceId));
		JSONObject workspaceJson = SimpleMetaStoreUtil.readMetaFile(userMetaFolder, workspaceId);
		verifyWorkspaceJson(workspaceJson, userId, workspaceId, workspaceName, projectNames);
	}

	/**
	 * Verifies the test user has the specified number of workspaces.
	 * @param workspaces Number of workspaces.
	 */
	protected void verifyWorkspaceRequest(List<String> workspaceIds) throws Exception {
		WebRequest request = new GetMethodWebRequest(SERVER_LOCATION + "/workspace");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("application/json", response.getContentType());
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No workspace information in response", responseObject);
		String userId = responseObject.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(userId);
		assertEquals(testUserId, responseObject.optString("UserName"));
		JSONArray workspaces = responseObject.optJSONArray("Workspaces");
		assertNotNull(workspaces);
		assertEquals(workspaceIds.size(), workspaces.length());
		for (String workspaceId : workspaceIds) {
			assertTrue(workspaces.toString().contains(workspaceId));
		}

	}
}
