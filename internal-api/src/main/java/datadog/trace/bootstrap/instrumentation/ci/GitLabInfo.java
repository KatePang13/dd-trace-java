package datadog.trace.bootstrap.instrumentation.ci;

class GitLabInfo extends CIProviderInfo {

  // https://docs.gitlab.com/ee/ci/variables/predefined_variables.html
  public static final String GITLAB = "GITLAB_CI";
  public static final String GITLAB_PROVIDER_NAME = "gitlab";
  public static final String GITLAB_PIPELINE_ID = "CI_PIPELINE_ID";
  public static final String GITLAB_PIPELINE_NAME = "CI_PROJECT_PATH";
  public static final String GITLAB_PIPELINE_NUMBER = "CI_PIPELINE_IID";
  public static final String GITLAB_PIPELINE_URL = "CI_PIPELINE_URL";
  public static final String GITLAB_STAGE_NAME = "CI_JOB_STAGE";
  public static final String GITLAB_JOB_NAME = "CI_JOB_NAME";
  public static final String GITLAB_JOB_URL = "CI_JOB_URL";
  public static final String GITLAB_WORKSPACE_PATH = "CI_PROJECT_DIR";
  public static final String GITLAB_GIT_REPOSITORY_URL = "CI_REPOSITORY_URL";
  public static final String GITLAB_GIT_COMMIT = "CI_COMMIT_SHA";
  public static final String GITLAB_GIT_BRANCH = "CI_COMMIT_BRANCH";
  public static final String GITLAB_GIT_TAG = "CI_COMMIT_TAG";

  GitLabInfo() {
    this.ciTags =
        CITagsBuilder.from(this.ciTags)
            .withCiProviderName(GITLAB_PROVIDER_NAME)
            .withCiPipelineId(System.getenv(GITLAB_PIPELINE_ID))
            .withCiPipelineName(System.getenv(GITLAB_PIPELINE_NAME))
            .withCiPipelineNumber(System.getenv(GITLAB_PIPELINE_NUMBER))
            .withCiPipelineUrl(buildPipelineUrl())
            .withCiStageName(System.getenv(GITLAB_STAGE_NAME))
            .withCiJobName(System.getenv(GITLAB_JOB_NAME))
            .withCiJorUrl(System.getenv(GITLAB_JOB_URL))
            .withCiWorkspacePath(getWorkspace())
            .withGitRepositoryUrl(
                filterSensitiveInfo(System.getenv(GITLAB_GIT_REPOSITORY_URL)),
                getLocalGitRepositoryUrl())
            .withGitCommit(getGitCommit(), getLocalGitCommitSha())
            .withGitBranch(normalizeRef(System.getenv(GITLAB_GIT_BRANCH)), getLocalGitBranch())
            .withGitTag(normalizeRef(System.getenv(GITLAB_GIT_TAG)), getLocalGitTag())
            .build();
  }

  @Override
  protected String buildGitCommit() {
    return System.getenv(GITLAB_GIT_COMMIT);
  }

  @Override
  protected String buildWorkspace() {
    return System.getenv(GITLAB_WORKSPACE_PATH);
  }

  private String buildPipelineUrl() {
    final String pipelineUrl = System.getenv(GITLAB_PIPELINE_URL);
    if (pipelineUrl == null || pipelineUrl.isEmpty()) {
      return null;
    }

    return pipelineUrl.replace("/-/pipelines/", "/pipelines/");
  }
}
