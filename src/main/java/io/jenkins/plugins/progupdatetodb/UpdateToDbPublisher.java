package io.jenkins.plugins.progupdatetodb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.utils.URIBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;

public class UpdateToDbPublisher extends Recorder {
  private final static String PNGPATH = "/target/screenshot/";
  private final static String JENKINSHOMEDIR = "/var/jenkins_home";
  private final static String WORKSPACEDIR = JENKINSHOMEDIR + "/workspace/";
  private final static String PNG = ".png";
  private final String progeduAPIUrl;
  private final String jenkinsJobName;

  @DataBoundConstructor
  public UpdateToDbPublisher(String progeduAPIUrl, String jenkinsJobName) {
    this.progeduAPIUrl = progeduAPIUrl;
    this.jenkinsJobName = jenkinsJobName;
  }

  public String getProgeduAPIUrl() {
    return progeduAPIUrl;
  }

  public String getJenkinsJobName() {
    return jenkinsJobName;
  }

  // --step 1--
  private ArrayList searchPngFile(BuildListener listener) {
    String pngFilePath = WORKSPACEDIR + jenkinsJobName + PNGPATH;
    ArrayList pngFile = new ArrayList<>();
    File pngfileDir = new File(pngFilePath);
    FilenameFilter filter = new FilenameFilter() {
      public boolean accept(File pngfileDir, String fileName) {
        return fileName.endsWith(PNG);
      }
    };

    String[] children = pngfileDir.list(filter);

    if (children == null) {
      listener.getLogger().println("dir does not exist or is not a dir");
    } else {
      for (int i = 0; i < children.length; i++) {
        String fileName = children[i];
        pngFile.add(fileName.substring(0, fileName.length() - PNG.length()));
      }
    }
    return pngFile;
  }

  private int getCommitCount() {
    int commitCount = 0;
    String checkurl = progeduAPIUrl + "/commits/screenshot/nextCommitNumber";
    try {
      URI uri = new URIBuilder(checkurl).addParameter("jobName", jenkinsJobName).build();
      HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
      conn.setReadTimeout(10000);
      conn.setConnectTimeout(15000);
      conn.setRequestMethod("GET");
      conn.connect();

      BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      commitCount = Integer.parseInt(br.readLine().toString());

      conn.disconnect();
      br.close();
    } catch (IOException | URISyntaxException e) {
      e.printStackTrace();
    }
    return commitCount;
  }

  private List<String> getPngUrl(List<String> pngFiles) {
    List<String> pngUrl = new ArrayList<String>();
    int commitCount = getCommitCount();
    for (int i = 0; i < pngFiles.size(); i++) {
      pngUrl.add("/job/" + jenkinsJobName + "/" + commitCount + "/artifact/target/screenshot/" + pngFiles.get(i) + ".png");
    }
    return pngUrl;
  }

  // --step 1/--
  // --step 2--
  private void saveURLtoDB(List<String> pngurl, BuildListener listener) {
    String checkurl = progeduAPIUrl + "/commits/screenshot/updateURL";
    try {
      URIBuilder uriBuilder = new URIBuilder(checkurl).addParameter("proName", jenkinsJobName);
      for (String data : pngurl) {
        uriBuilder.addParameter("url", data);
      }
      URI uri = uriBuilder.build();
      String url = URLDecoder.decode(uri.toURL().toString(), "UTF-8");
      URL finalUrl = new URL(url);
      HttpURLConnection conn = (HttpURLConnection) finalUrl.openConnection();
      conn.setReadTimeout(10000);
      conn.setConnectTimeout(15000);
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      conn.setRequestProperty("charset", "utf-8");
      conn.setRequestMethod("POST");
      conn.connect();

      int responseCode = conn.getResponseCode();
      listener.getLogger().println("Response Code : " + responseCode);

      BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      StringBuilder sb = new StringBuilder();
      String line;

      while ((line = br.readLine()) != null) {
        sb.append(line + "\n");
      }

      listener.getLogger().println("WEB return value is : " + sb);

      conn.disconnect();
      br.close();
    } catch (IOException | URISyntaxException e) {
      e.printStackTrace();
    }
  }

  // --/step 2--
  @Override
  public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
    listener.getLogger()
    .println("-----------------------UpdateScreenshotPNGToDB-----------------------------");
    // step 1: Search all PNG file under src/test/screenshot/
    List<String> pngFiles = new ArrayList<>();
    pngFiles = searchPngFile(listener);
    List<String> pngurl = getPngUrl(pngFiles);

    // step 2: save to DB
    saveURLtoDB(pngurl, listener);
    listener.getLogger()
    .println("-----------------------UpdateScreenshotPNGToDB-----------------------------");
    return true;
  }

  // Overridden for better type safety.
  // If your plugin doesn't really define any property on Descriptor,
  // you don't have to do this.
  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  @Override
  public BuildStepMonitor getRequiredMonitorService() {
    // TODO Auto-generated method stub
    return BuildStepMonitor.NONE;
  }

  @Symbol("greet")
  @Extension // This indicates to Jenkins that this is an implementation of an
             // extension point.
  public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
    // public DescriptorImpl() {
    // load();
    // }

    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      // Indicates that this builder can be used with all kinds of project types
      return true;
    }

    /**
     * This human readable name is used in the configuration screen.
     */
    public String getDisplayName() {
      return "ProgEdu Update Screenshot PNG to Database";
    }
  }

}
