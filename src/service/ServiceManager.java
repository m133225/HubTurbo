package service;

import static org.eclipse.egit.github.core.client.IGitHubConstants.SEGMENT_REPOS;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import model.Model;
import model.TurboLabel;
import model.TurboMilestone;
import model.TurboUser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.egit.github.core.Comment;
import org.eclipse.egit.github.core.IRepositoryIdProvider;
import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.Label;
import org.eclipse.egit.github.core.Milestone;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubRequest;
import org.eclipse.egit.github.core.client.RequestException;
import org.eclipse.egit.github.core.service.CollaboratorService;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.IssueService;
import org.eclipse.egit.github.core.service.MarkdownService;
import org.eclipse.egit.github.core.service.MilestoneService;
import org.markdown4j.Markdown4jProcessor;

import service.updateservice.CommentDownloader;
import service.updateservice.ModelUpdater;
import storage.CacheFileHandler;
import storage.CachedRepoData;
import tests.stubs.ServiceManagerStub;
import ui.UI;
import ui.components.HTStatusBar;

/**
 * Singleton class that provides access to the GitHub API services required by
 * HubTurbo
 * 
 * Only data from a single repository can be loaded at any point of time. The
 * currently loaded repository is stored in the application's ServiceManager
 * instance
 * 
 * Also holds a reference to the application's current Model instance, which
 * stores the repository's labels, milestones, assignees and issues.
 * */
@SuppressWarnings("unused")
public class ServiceManager {

	private static final ServiceManager instance = new ServiceManager();

	// Set externally by test runners
	public static boolean isInTestMode = false;

	public static ServiceManager getInstance() {
		if (!isInTestMode) {
			return instance;
		} else {
			return new ServiceManagerStub();
		}
	}

	private static final Logger logger = LogManager.getLogger(ServiceManager.class.getName());

	public static final String KEY_ISSUES = "issues";
	public static final String KEY_MILESTONES = "milestones";
	public static final String KEY_LABELS = "labels";
	public static final String KEY_COLLABORATORS = "collaborators";

	// Login state

	protected String lastUsedPassword;

	// Services
	
	private GitHubClientExtended githubClient;

	private CollaboratorService collabService;
	private IssueServiceExtended issueService;
	private LabelServiceFixed labelService;
	private MilestoneService milestoneService;
	private RepositoryServiceExtended repositoryService;
	private MarkdownService markdownService;
	private ContentsService contentService;

	// Model updates
	
	private ModelUpdater modelUpdater;
	private CommentDownloader commentDownloader = new CommentDownloader(this);
	protected Model model;
	protected RepositoryId repoId;
	private String issuesETag = null;
	private String collabsETag = null;
	private String labelsETag = null;
	private String milestonesETag = null;
	private String issueCheckTime = null;

	private final Executor immediateExecutor = Executors.newSingleThreadExecutor();

	private static final int REFRESH_INTERVAL = 60;

	private static final int TICK_INTERVAL = 1;
	private final ScheduledExecutorService timeUntilRefreshExecutor = Executors.newScheduledThreadPool(1);
	private ScheduledFuture<?> timeUntilRefreshResult;

	// All method which use these fields should be synchronised.
	private int _timeRemainingUntilRefresh = REFRESH_INTERVAL;
	private boolean _isPeriodicUpdatePaused = false;

	private static final String ISSUE_STATE_ALL = "all";
	public static final String STATE_OPEN = "open";
	public static final String STATE_CLOSED = "closed";

	protected ServiceManager() {
		githubClient = new GitHubClientExtended();
		collabService = new CollaboratorService(githubClient);
		issueService = new IssueServiceExtended(githubClient);
		labelService = new LabelServiceFixed(githubClient);
		milestoneService = new MilestoneService(githubClient);
		repositoryService = new RepositoryServiceExtended(githubClient);
		markdownService = new MarkdownService(githubClient);
		contentService = new ContentsService(githubClient);
		// TODO construct model later
		model = new Model();
	}

	/**
	 * Methods concerned with login logic
	 */
	private void ______LOGIN______() {
	}

	/**
	 * Given a username and password, attempts to log into GitHub.
	 * Returns true on success and false otherwise.
	 * @param userId
	 * @param password
	 * @return
	 */
	public boolean login(String userId, String password) {
		
		this.lastUsedPassword = password;
		githubClient.setCredentials(userId, password);
		
		// Attempt login
		try {
			GitHubRequest request = new GitHubRequest();
			request.setUri("/");
			githubClient.get(request);
		} catch (IOException e) {
			// Login failed
			logger.error(e.getLocalizedMessage(), e);
			return false;
		}
		return true;
	}

	/**
	 * Returns the username last used to log in.
	 * @return
	 */
	public String getUserId() {
		return githubClient.getUser();
	}

	/**
	 * Returns the password last used to log in.
	 * @return
	 */
	public String getLastUsedPassword() {
		assert lastUsedPassword != null;
		return lastUsedPassword;
	}

	/**
	 * Methods concerned with dealing with online GitHub repositories
	 */
	private void ______REPOSITORY______() {
	}

	// TODO should return a copy
	public IRepositoryIdProvider getRepoId() {
		return repoId;
	}

	/**
	 * Given a repository owner and name, loads its contents into the model.
	 * Assumes that authentication has already been done, so should be called
	 * after {@link #login(String, String) login}.
	 * @param owner
	 * @param name
	 * @return
	 * @throws IOException
	 */
	public boolean setupRepository(String owner, String name) throws IOException {

		assert lastUsedPassword != null : "setupRepository should be called only after login";
		logger.info("Authenticating...");

		this.repoId = RepositoryId.create(owner, name);

		if (isRepositoryValid(repoId)) {
			return model.loadComponents(repoId);
		} else {
			// TODO: create specific exception for this
			throw new IOException("Cannot access repository");
		}
	}

	public String getRepoOwner() {
		return repoId.getOwner();
	}

	public String getRepoName() {
		return repoId.getName();
	}

	/**
	 * Determines if a repository is a valid one. Returns false if not, otherwise
	 * returns true. Throws an IOException if the check fails in any other way.
	 * @param repo
	 * @return
	 * @throws IOException
	 */
	public boolean isRepositoryValid(IRepositoryIdProvider repo) throws IOException {
		return isRepositoryValid(repo.generateId());
	}

	/**
	 * Determines if a repository is a valid one. Returns false if not, otherwise
	 * returns true. Throws an IOException if the check fails in any other way.
	 * @param repo
	 * @return
	 * @throws IOException
	 */
	private boolean isRepositoryValid(String repo) throws IOException {
		String repoURL = SEGMENT_REPOS + "/" + repo;
		return checkRepositoryValidity(repoURL);
	}

	/**
	 * Determines if a repository is a valid one. Returns false if not, otherwise
	 * returns true. Throws an IOException if the check fails in any other way.
	 * @param repo
	 * @return
	 * @throws IOException
	 */
	protected boolean checkRepositoryValidity(String uri) throws IOException {
		try {
			GitHubRequest req = new GitHubRequest();
			githubClient.get(req.setUri(uri));
			return true;
		} catch (RequestException e) {
			logger.error(e.getLocalizedMessage(), e);
			if (e.getStatus() == HttpURLConnection.HTTP_NOT_FOUND) {
				return false;
			}
			throw e;
		}
	}

	public int getRemainingRequests() {
		return githubClient.getRemainingRequests();
	}

	public int getRequestLimit() {
		return githubClient.getRequestLimit();
	}

	/**
	 * Returns a list of the user's public repositories.
	 * @return
	 * @throws IOException
	 */
	public List<Repository> getRepositories() throws IOException {
		return repositoryService.getRepositories();
	}

	/**
	 * Returns a list of the names of the user's public repositories
	 * @return
	 * @throws IOException
	 */
	public List<String> getRepositoriesNames() throws IOException {
		return repositoryService.getRepositoriesNames(getUserId());
	}

	/**
	 * Returns a list of the public repositories belonging to the user and the
	 * user's organisations
	 * @return
	 * @throws IOException
	 */
	public List<Repository> getAllRepositories() throws IOException {
		return repositoryService.getAllRepositories(getUserId());
	}

	/**
	 * Returns a list of the names of the public repositories belonging to the
	 * user and the user's organisations
	 * */
	public List<String> getAllRepositoryNames() throws IOException {
		return repositoryService.getAllRepositoriesNames(getUserId());
	}

	/**
	 * Methods concerned with updating or interfacing with the model
	 */
	private void ______MODEL______() {
	}

	@SuppressWarnings("rawtypes")
	public HashMap<String, List> getResources(RepositoryId repoId) throws IOException {
		this.repoId = repoId;
	
		CacheFileHandler dcHandler = new CacheFileHandler(repoId.toString());
		// TODO set these paramters in constructor instead
		model.setDataCacheFileHandler(dcHandler);
		model.setRepoId(repoId);
	
		boolean needToGetResources = true;
		
		CachedRepoData repo = dcHandler.getRepo();
		if (repo != null) {
			needToGetResources = false;
		}
	
		if (!needToGetResources) {
			logger.info("Loading from cache...");
			issuesETag = repo.getIssuesETag();
			collabsETag = repo.getCollaboratorsETag();
			labelsETag = repo.getLabelsETag();
			milestonesETag = repo.getMilestonesETag();
			issueCheckTime = repo.getIssueCheckTime();
			List<TurboUser> collaborators = repo.getCollaborators();
			List<TurboLabel> labels = repo.getLabels();
			List<TurboMilestone> milestones = repo.getMilestones();
			// Delay getting of issues until labels and milestones are loaded in Model
	
			HashMap<String, List> map = new HashMap<String, List>();
			map.put(KEY_COLLABORATORS, collaborators);
			map.put(KEY_LABELS, labels);
			map.put(KEY_MILESTONES, milestones);
			return map;
		} else {
			logger.info("Cache not found, loading data from GitHub...");
			return getGitHubResources();
		}
	}

	@SuppressWarnings("rawtypes")
	public HashMap<String, List> getGitHubResources() throws IOException {
		issuesETag = null;
		collabsETag = null;
		labelsETag = null;
		milestonesETag = null;
		issueCheckTime = null;
	
		List<User> ghCollaborators = new ArrayList<User>();
		List<Label> ghLabels = new ArrayList<Label>();
		List<Milestone> ghMilestones = new ArrayList<Milestone>();
		List<Issue> ghIssues = new ArrayList<Issue>();
	
		ghLabels = getLabels();
		ghMilestones = getMilestones();
		ghIssues = getAllIssues();
	
		HashMap<String, List> map = new HashMap<String, List>();
		map.put(KEY_COLLABORATORS, ghCollaborators);
		map.put(KEY_LABELS, ghLabels);
		map.put(KEY_MILESTONES, ghMilestones);
		map.put(KEY_ISSUES, ghIssues);
		return map;
	}

	public Model getModel() {
		return model;
	}

	// TODO change to optional or somehow remove null return value (assertion?)
	public Date getLastModelUpdateTime() {
		if (modelUpdater != null) {
			return modelUpdater.getLastUpdateTime();
		}
		return null;
	}
	
	private CountDownLatch updateModelNow(boolean log) {
		if (log) logger.info("Updating model now");
		
		final String repoId = model.getRepoId().generateId();
		final CountDownLatch latch = new CountDownLatch(4);
		
		modelUpdater = new ModelUpdater(githubClient, model, issuesETag, collabsETag, labelsETag, milestonesETag,
				issueCheckTime);
		
		immediateExecutor.execute(() -> {
			preventRepoSwitchingAndUpdateModel(latch, repoId);
		});

		// The latch is returned. Thus there will be two threads blocking on it:
		// this one, and whatever is calling this method to update the model.
		return latch;
	}
	
	public CountDownLatch updateModelNow() {
		return updateModelNow(true);
	}
	
	private void updateModelPeriodically(boolean log) {

		if (log) logger.info("Starting model updates (without updating immediately)");

		// Ensure that model update isn't ongoing
		stopPeriodicModelUpdates(false);

		modelUpdater = new ModelUpdater(githubClient, model, issuesETag, collabsETag, labelsETag, milestonesETag,
				issueCheckTime);

		// We get the repo id from the model now. On task completion, the
		// repo id may be different if the project was switched, so we
		// validate with this repo id at that point.

		final String repoId = model.getRepoId().generateId();

		timeUntilRefreshResult = timeUntilRefreshExecutor.scheduleWithFixedDelay(() -> {
			boolean shouldUpdate = updateTimeRemainingUntilRefresh();
			HTStatusBar.updateRefreshTimer(getTimeRemainingUntilRefresh());

			if (shouldUpdate) {
				preventRepoSwitchingAndUpdateModel(new CountDownLatch(4), repoId);
			}
		}, 0, TICK_INTERVAL, TimeUnit.SECONDS);
	}

	public void updateModelPeriodically() {
		updateModelPeriodically(true);
	}
	
	public void preventRepoSwitchingAndUpdateModel(CountDownLatch latch, String repoId) {

		pauseTimer();

		// Wait for repository selection to be disabled
		CountDownLatch continuation = new CountDownLatch(1);
		Platform.runLater(() -> {
			UI.getInstance().disableRepositorySwitching();
			continuation.countDown();
		});
		try {
			continuation.await();
		} catch (InterruptedException e) {
			logger.error(e.getLocalizedMessage(), e);
		}
		
		// Wait for the update to complete
		modelUpdater.updateModel(latch, repoId);
		try {
			latch.await();
		} catch (InterruptedException e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	  	model.updateCache();
	  	
		commentDownloader.download();
	  	model.triggerModelChangeEvent();
		
		// Reset progress UI
		HTStatusBar.updateProgress(0);
		
		// Enable repository switching
		Platform.runLater(() -> {
			UI.getInstance().enableRepositorySwitching();
		});
		
		unpauseTimer();
	}
	
	private void stopPeriodicModelUpdates(boolean log) {

		// If the model update was never started, don't do anything
		if (timeUntilRefreshResult == null || timeUntilRefreshResult.isCancelled())
			return;

		timeUntilRefreshResult.cancel(true);
		resetTimeRemainingUntilRefresh();

		// Indicate that model update has been stopped
		timeUntilRefreshResult = null;
		
		if (log) logger.info("Stopped model update");
	}

	public void stopModelUpdate() {
		stopPeriodicModelUpdates(true);
	}

	public void updateModelNowAndPeriodically() {
		logger.info("Updating model now, and starting periodic update");
		updateModelNow(false);
		updateModelPeriodically(false);
	}
	
	public synchronized int getTimeRemainingUntilRefresh() {
		return _timeRemainingUntilRefresh;
	}
	
	public synchronized void resetTimeRemainingUntilRefresh() {
		_timeRemainingUntilRefresh = REFRESH_INTERVAL;
	}

	/**
	 * Updates {@link timeRemainingUntilRefresh}, returning true if an update should occur.
	 * @return
	 */
	private synchronized boolean updateTimeRemainingUntilRefresh() {
		if (_timeRemainingUntilRefresh == 1) {
			resetTimeRemainingUntilRefresh();
			return true;
		} else if (!isTimerPaused()) {
			--_timeRemainingUntilRefresh;
		}
		return false;
	}
	
	private synchronized boolean isTimerPaused() {
		return _isPeriodicUpdatePaused;
	}

	private synchronized void pauseTimer() {
		assert !_isPeriodicUpdatePaused : "Attempt to pause timer that is already paused";
		_isPeriodicUpdatePaused = true;
	}

	private synchronized void unpauseTimer() {
		assert _isPeriodicUpdatePaused : "Attempt to unpause timer that is not paused";
		_isPeriodicUpdatePaused = false;
	}

	/**
	 * Called on application exit. Will be called only once.
	 */
	public void shutdownModelUpdate() {
		stopModelUpdate();
		timeUntilRefreshExecutor.shutdown();
	}

	private void ______LABELS______() {
	}

	public List<Label> getLabels() throws IOException {
		if (repoId != null) {
			return labelService.getLabels(repoId);
		}
		return new ArrayList<Label>();
	}

	public Label createLabel(Label ghLabel) throws IOException {
		if (repoId != null) {
			return labelService.createLabel(repoId, ghLabel);
		}
		return null;
	}

	public void deleteLabel(String label) throws IOException {
		if (repoId != null) {
			labelService.deleteLabel(repoId, label);
		}
	}

	public Label editLabel(Label label, String name) throws IOException {
		if (repoId != null) {
			return (Label) labelService.editLabel(repoId, label, name);
		}
		return null;
	}

	private void ______MILESTONES______() {
	}

	public List<Milestone> getMilestones() throws IOException {
		if (repoId != null) {
			return milestoneService.getMilestones(repoId, ISSUE_STATE_ALL);
		}
		return new ArrayList<Milestone>();
	}

	public Milestone createMilestone(Milestone milestone) throws IOException {
		if (repoId != null) {
			return milestoneService.createMilestone(repoId, milestone);
		}
		return null;
	}

	public void deleteMilestone(int milestoneNum) throws IOException {
		if (repoId != null) {
			milestoneService.deleteMilestone(repoId, milestoneNum);
		}
	}

	public Milestone editMilestone(Milestone milestone) throws IOException {
		if (repoId != null) {
			return (Milestone) milestoneService.editMilestone(repoId, milestone);
		}
		return null;
	}

	private void ______ISSUES______() {
	}

	public List<Issue> getAllIssues() throws IOException {
		if (repoId != null) {
			Map<String, String> filters = new HashMap<String, String>();
			filters.put(IssueService.FIELD_FILTER, ISSUE_STATE_ALL);
			filters.put(IssueService.FILTER_STATE, ISSUE_STATE_ALL);
			return issueService.getIssues(repoId, filters);
		}
		return new ArrayList<Issue>();
	}

	public Issue createIssue(Issue issue) throws IOException {
		if (repoId != null) {
			return issueService.createIssue(repoId, issue);
		}
		return null;
	}

	public Issue getIssue(int issueId) throws IOException {
		if (repoId != null) {
			return issueService.getIssue(repoId, issueId);
		}
		return null;
	}

	public HashMap<String, Object> getIssueData(int issueId) throws IOException {
		if (repoId != null) {
			return issueService.getIssueData(repoId, issueId);
		}
		return new HashMap<String, Object>();
	}

	public String getDateFromIssueData(HashMap<String, Object> issueData) {
		return (String) issueData.get(IssueServiceExtended.ISSUE_DATE);
	}

	/**
	 * Get user repositories
	 * */

	public Issue getIssueFromIssueData(HashMap<String, Object> issueData) {
		return (Issue) issueData.get(IssueServiceExtended.ISSUE_CONTENTS);
	}

	public Issue editIssue(Issue latest, String dateModified) throws IOException {
		if (repoId != null) {
			return (Issue) issueService.editIssue(repoId, latest, dateModified);
		}
		return null;
	}

	public Issue editIssueTitle(int issueId, String title) throws IOException {
		if (repoId != null) {
			return issueService.editIssueTitle(repoId, issueId, title);
		}
		return null;
	}

	public Issue editIssueBody(int issueId, String body) throws IOException {
		if (repoId != null) {
			return issueService.editIssueBody(repoId, issueId, body);
		}
		return null;
	}

	public void closeIssue(int issueId) throws IOException {
		if (repoId != null) {
			issueService.editIssueState(repoId, issueId, false);
		}
	}

	public void openIssue(int issueId) throws IOException {
		if (repoId != null) {
			issueService.editIssueState(repoId, issueId, true);
		}
	}

	/**
	 * Methods to work with issue labels
	 * */

	public List<Label> setLabelsForIssue(long issueId, List<Label> labels) throws IOException {
		if (repoId != null) {
			return labelService.setLabels(repoId, Long.toString(issueId), labels);
		}
		return new ArrayList<Label>();
	}

	/**
	 * Adds list of labels to a github issue. Returns all the labels for the
	 * issue.
	 * */
	public List<Label> addLabelsToIssue(int issueId, List<Label> labels) throws IOException {
		if (repoId != null) {
			return labelService.addLabelsToIssue(repoId, Integer.toString(issueId), labels);
		}
		return new ArrayList<Label>();
	}

	public void deleteLabelsFromIssue(int issueId, List<Label> labels) throws IOException {
		for (Label label : labels) {
			deleteLabelFromIssue(issueId, label);
		}
	}

	public void deleteLabelFromIssue(int issueId, Label label) throws IOException {
		if (repoId != null) {
			labelService.deleteLabelFromIssue(repoId, Integer.toString(issueId), label);
		}
	}

	public boolean setIssueMilestone(int issueId, Milestone milestone) throws IOException {
		if (repoId != null) {
			Issue result = issueService.setIssueMilestone(repoId, issueId, milestone);
			Milestone resMilestone = result.getMilestone();
			if (resMilestone == null) {
				return milestone == null;
			} else {
				return milestone.getNumber() == resMilestone.getNumber();
			}
		}
		return false;
	}

	public boolean setIssueAssignee(int issueId, User user) throws IOException {
		if (repoId != null) {
			Issue result = issueService.setIssueAssignee(repoId, issueId, user);
			User assignee = result.getAssignee();
			if (assignee == null) {
				return user == null;
			} else {
				return assignee.getLogin().equals(user.getLogin());
			}
		}
		return false;
	}

	private void ______COLLABORATORS______() {
	}

	public List<User> getCollaborators() throws IOException {
		if (repoId != null) {
			return collabService.getCollaborators(repoId);
		}
		return new ArrayList<User>();
	}

	private void ______EVENTS______() {
	}

	public List<TurboIssueEvent> getFeeds(int issueNum) throws IOException {
		GitHubEventsResponse ghEventsResponse = issueService.getIssueEvents(getRepoId(), issueNum);
		return ghEventsResponse.getTurboIssueEvents();
	}

	/**
	 * Gets events for a issue from GitHub, or returns a cached version if
	 * already present.
	 * 
	 * @param issueId
	 * @return
	 * @throws IOException
	 */
	public ArrayList<TurboIssueEvent> getEvents(int issueId) throws IOException {
		if (repoId != null) {
			return issueService.getIssueEvents(repoId, issueId).getTurboIssueEvents();
		}
		return new ArrayList<>();
	}

	private void ______MARKDOWN______() {
	}

	public String getContentMarkup(final String text) throws IOException {
		if (text.contains("#")) {
			return getRepositoryHtml(text);
		}
		return new Markdown4jProcessor().process(text);
	}

	public String getRepositoryHtml(final String text) throws IOException {
		if (repoId != null) {
			return markdownService.getRepositoryHtml(repoId, text);
		} else {
			return "";
		}
	}

	public String getHtml(final String text, final String mode) throws IOException {
		return markdownService.getHtml(text, mode);
	}

	private void ______REPOSITORY_CONTENTS______() {
	}

	public List<RepositoryContents> getContents(IRepositoryIdProvider repository) throws IOException {
		return contentService.getContents(repository);
	}

	public List<RepositoryContents> getContents(IRepositoryIdProvider repository, String path) throws IOException {
		return contentService.getContents(repository, path);
	}

	private void ______COMMENTS______() {
	}

	public List<Comment> getLatestComments(int issueId) throws IOException {
		if (repoId != null) {
			List<Comment> comments = issueService.getComments(repoId, issueId);
			return comments;
		}
		return new ArrayList<Comment>();
	}
}
