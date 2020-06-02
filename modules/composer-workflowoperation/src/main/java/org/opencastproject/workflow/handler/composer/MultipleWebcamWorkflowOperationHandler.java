/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package org.opencastproject.workflow.handler.composer;

import static java.lang.String.format;
import static org.opencastproject.util.data.Collections.map;

import org.opencastproject.composer.api.ComposerService;
import org.opencastproject.composer.api.EncoderException;
import org.opencastproject.composer.layout.Dimension;
import org.opencastproject.inspection.api.MediaInspectionException;
import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElementBuilder;
import org.opencastproject.mediapackage.MediaPackageElementBuilderFactory;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.Track;
import org.opencastproject.mediapackage.TrackSupport;
import org.opencastproject.mediapackage.VideoStream;
import org.opencastproject.mediapackage.identifier.IdBuilderFactory;
import org.opencastproject.mediapackage.selector.TrackSelector;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.smil.api.util.SmilUtil;
import org.opencastproject.util.IoSupport;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.data.Tuple;
import org.opencastproject.util.data.VCell;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationInstance;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workspace.api.Workspace;

import com.entwinemedia.fn.data.Opt;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.smil.SMILDocument;
import org.w3c.dom.smil.SMILElement;
import org.w3c.dom.smil.SMILMediaElement;
import org.w3c.dom.smil.SMILParElement;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The workflow definition for handling multiple webcam videos that have overlapping playtime
 * Checks which videos of those webcam videos are currently playing and dynamically scales them to fit in a single video
 *
 * Relies on a smil with videoBegin and duration times, as is created by ingest through addPartialTrack
 *
 * Returns a bunch of numerated videos to the target flavor
 * FIXME: These videos lack metadata, SO AN IMMEDIATE INSPECT OPERATION IS REQUIRED
 * These videos are really just parts, SO A CONCAT OPERATION IS VERY HIGHLY RECOMMENDED
 */
public class MultipleWebcamWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  /** Workflow configuration keys */
  private static final String SOURCE_PRESENTER_FLAVOR = "source-presenter-flavor";
  private static final String SOURCE_PRESENTATION_FLAVOR = "source-presentation-flavor";
  private static final String SOURCE_SMIL_FLAVOR = "source-smil-flavor";

  private static final String TARGET_PRESENTER_FLAVOR = "target-presenter-flavor";

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(PartialImportWorkflowOperationHandler.class);

  /** Constants */
  private static final String EMPTY_VALUE = "";
  private static final String NODE_TYPE_VIDEO = "video";
  private static final String FLAVOR_AUDIO_SUFFIX = "-audio";
  private static final String PRESENTER_KEY = "presenter";

  // TODO: Make ffmpeg commands more "opencasty"
  private static final String[] FFMPEG = {"ffmpeg", "-y", "-v", "warning", "-nostats", "-max_error_rate", "1.0"};
  private static final String FFMPEG_WF_CODEC = "h264"; //"mpeg2video";
  private static final int FFMPEG_WF_FRAMERATE = 24;
  private static final String[] FFMPEG_WF_ARGS = {"-an", "-codec", FFMPEG_WF_CODEC, "-q:v", "2", "-g", Integer.toString(FFMPEG_WF_FRAMERATE * 10), "-pix_fmt", "yuv420p", "-r", Integer.toString(FFMPEG_WF_FRAMERATE)};
  private static final String WF_EXT = "ts";

  /** The composer service */
  private ComposerService composerService = null;

  /** The local workspace */
  private Workspace workspace = null;

  private ServiceRegistry serviceRegistry;


  /**
   * Callback for the OSGi declarative services configuration.
   *
   * @param composerService
   *          the local composer service
   */
  public void setComposerService(ComposerService composerService) {
    this.composerService = composerService;
  }

  /**
   * Callback for declarative services configuration that will introduce us to the local workspace service.
   * Implementation assumes that the reference is configured as being static.
   *
   * @param workspace
   *          an instance of the workspace
   */
  public void setWorkspace(Workspace workspace) {
    this.workspace = workspace;
  }

  public void setServiceRegistry(final ServiceRegistry serviceRegistry) {
    this.serviceRegistry = serviceRegistry;
  }


  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.workflow.api.WorkflowOperationHandler#start(org.opencastproject.workflow.api.WorkflowInstance,
   *      JobContext)
   */
  @Override
  public WorkflowOperationResult start(final WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {
    logger.debug("Running multiple webcam workflow operation on workflow {}", workflowInstance.getId());

    List<MediaPackageElement> elementsToClean = new ArrayList<MediaPackageElement>();

    try {
      return main(workflowInstance.getMediaPackage(), workflowInstance.getCurrentOperation(), elementsToClean);
    } catch (Exception e) {
      throw new WorkflowOperationException(e);
    } finally {
      for (MediaPackageElement elem : elementsToClean) {
        try {
          workspace.delete(elem.getURI());
        } catch (Exception e) {
          logger.warn("Unable to delete element {}: {}", elem, e);
        }
      }
    }
  }

  class LayoutArea
  {
    private int x = 0;
    private int y = 0;
    private int width = 1337;
    private int height = 42;
    private String name = "webcam";

    public int getX() {
      return x;
    }
    public void setX(int x) {
      this.x = x;
    }
    public int getY() {
      return y;
    }
    public void setY(int y) {
      this.y = y;
    }
    public int getWidth() {
      return width;
    }
    public void setWidth(int width) {
      this.width = width;
    }
    public int getHeight() {
      return height;
    }
    public void setHeight(int height) {
      this.height = height;
    }
    public String getName() {
      return name;
    }
    public void setName(String name) {
      this.name = name;
    }

    LayoutArea(String name, int x, int y, int width, int height) {
      this.name = name;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }
  }

  class VideoInfo
  {
    private int aspectRatioWidth = 16;
    private int aspectRatioHeight = 9;
    private long timeStamp = 0;
    private long nextTimeStamp = 0;
    private long startTime = 0;
    private long duration = 0;
    private String codec = "codec";
    private String filename = "filename.mp4";

    public int getAspectRatioWidth() {
      return aspectRatioWidth;
    }
    public void setAspectRatioWidth(int aspectRatioWidth) {
      this.aspectRatioWidth = aspectRatioWidth;
    }
    public int getAspectRatioHeight() {
      return aspectRatioHeight;
    }
    public void setAspectRatioHeight(int aspectRatioHeight) {
      this.aspectRatioHeight = aspectRatioHeight;
    }
    public long getTimeStamp() {
      return timeStamp;
    }
    public void setTimeStamp(long timeStamp) {
      this.timeStamp = timeStamp;
    }
    public long getNextTimeStamp() {
      return nextTimeStamp;
    }
    public void setNextTimeStamp(long nextTimeStamp) {
      this.nextTimeStamp = nextTimeStamp;
    }
    public long getStartTime() {
      return startTime;
    }
    public void setStartTime(long startTime) {
      this.startTime = startTime;
    }
    public long getDuration() {
      return duration;
    }
    public void setDuration(long duration) {
      this.duration = duration;
    }
    public String getCodec() {
      return codec;
    }
    public void setCodec(String codec) {
      this.codec = codec;
    }
    public String getFilename() {
      return filename;
    }
    public void setFilename(String filename) {
      this.filename = filename;
    }

    VideoInfo(int height, int width) {
      aspectRatioWidth = width;
      aspectRatioHeight = height;
    }

    VideoInfo() {

    }

  }

  class Offset
  {
    private int x = 16;
    private int y = 9;

    public int getX() {
      return x;
    }
    public void setX(int x) {
      this.x = x;
    }
    public int getY() {
      return y;
    }
    public void setY(int y) {
      this.y = y;
    }

    Offset(int x, int y) {
      this.x = x;
      this.y = y;
    }
  }

  class VideoEdl
  {
    private long timeStamp = 0;
    private long nextTimeStamp = 0;
    private List<Area> areas;

    public long getTimeStamp() {
      return timeStamp;
    }
    public void setTimeStamp(long timeStamp) {
      this.timeStamp = timeStamp;
    }
    public long getNextTimeStamp() {
      return nextTimeStamp;
    }
    public void setNextTimeStamp(long nextTimeStamp) {
      this.nextTimeStamp = nextTimeStamp;
    }
    public List<Area> getAreas() {
      return areas;
    }
    public void setAreas(List<Area> areas) {
      this.areas = areas;
    }

    VideoEdl()
    {
      areas = new ArrayList<Area>();
    }
  }

  class Area
  {
    private String filename = "";
    private long timeStamp = 0;
    private int aspectRatioWidth = 16;
    private int aspectRatioHeight = 9;
    private long startTime = 0;
    private String codec = "codec";

    public String getFilename() {
      return filename;
    }
    public void setFilename(String filename) {
      this.filename = filename;
    }
    public long getTimeStamp() {
      return timeStamp;
    }
    public void setTimeStamp(long timeStamp) {
      this.timeStamp = timeStamp;
    }
    public int getAspectRatioWidth() {
      return aspectRatioWidth;
    }
    public void setAspectRatioWidth(int aspectRatioWidth) {
      this.aspectRatioWidth = aspectRatioWidth;
    }
    public int getAspectRatioHeight() {
      return aspectRatioHeight;
    }
    public void setAspectRatioHeight(int aspectRatioHeight) {
      this.aspectRatioHeight = aspectRatioHeight;
    }
    public long getStartTime() {
      return startTime;
    }
    public void setStartTime(long startTime) {
      this.startTime = startTime;
    }
    public String getCodec() {
      return codec;
    }
    public void setCodec(String codec) {
      this.codec = codec;
    }

    Area(String filename, long timeStamp)
    {
      this.filename = filename;
      this.timeStamp = timeStamp;
    }

    Area(String filename, long timeStamp, int aspectRatioHeight, int aspectRatioWidth, String codec, long startTime)
    {
      this(filename, timeStamp);
      this.aspectRatioHeight = aspectRatioHeight;
      this.aspectRatioWidth = aspectRatioWidth;
      this.codec = codec;
      this.startTime = startTime;
    }
  }

  class StartStopEvent implements Comparable<StartStopEvent>
  {
    private boolean start;
    private long timeStamp;
    private String filename;
    private VideoInfo videoInfo;

    public boolean isStart() {
      return start;
    }
    public void setStart(boolean start) {
      this.start = start;
    }
    public long getTimeStamp() {
      return timeStamp;
    }
    public void setTimeStamp(long timeStamp) {
      this.timeStamp = timeStamp;
    }
    public String getFilename() {
      return filename;
    }
    public void setFilename(String filename) {
      this.filename = filename;
    }
    public VideoInfo getVideoInfo() {
      return videoInfo;
    }
    public void setVideoInfo(VideoInfo videoInfo) {
      this.videoInfo = videoInfo;
    }

    StartStopEvent(boolean start, String filename, long timeStamp, VideoInfo videoInfo)
    {
      this.start = start;
      this.timeStamp = timeStamp;
      this.filename = filename;
      this.videoInfo = videoInfo;
    }

    @Override
    public int compareTo(StartStopEvent o) {
      return this.timeStamp < o.timeStamp ? -1
              :
             this.timeStamp > o.timeStamp ? 1 : 0;
    }
  }

  private WorkflowOperationResult main(MediaPackage src, WorkflowOperationInstance operation,
          List<MediaPackageElement> elementsToClean) throws EncoderException, IOException, NotFoundException,
          MediaPackageException, WorkflowOperationException, ServiceRegistryException {
    final MediaPackage mediaPackage = (MediaPackage) src.clone();

    // Read config options
    final Opt<String> presenterFlavor = getOptConfig(operation, SOURCE_PRESENTER_FLAVOR);
    final Opt<String> presentationFlavor = getOptConfig(operation, SOURCE_PRESENTATION_FLAVOR);
    final MediaPackageElementFlavor smilFlavor = MediaPackageElementFlavor.parseFlavor(getConfig(operation, SOURCE_SMIL_FLAVOR));
    final MediaPackageElementFlavor targetPresenterFlavor = parseTargetFlavor(
            getConfig(operation, TARGET_PRESENTER_FLAVOR), "presenter");

    // Get tracks
    final TrackSelector presenterTrackSelector = mkTrackSelector(presenterFlavor);
    final TrackSelector presentationTrackSelector = mkTrackSelector(presentationFlavor);
    final List<Track> originalTracks = new ArrayList<Track>();
    final List<Track> presenterTracks = new ArrayList<Track>();
    final List<Track> presentationTracks = new ArrayList<Track>();
    // Collecting presenter tracks
    for (Track t : presenterTrackSelector.select(mediaPackage, false)) {
      logger.info("Found partial presenter track {}", t);
      originalTracks.add(t);
      presenterTracks.add(t);
    }
    // Collecting presentation tracks
    for (Track t : presentationTrackSelector.select(mediaPackage, false)) {
      logger.info("Found partial presentation track {}", t);
      originalTracks.add(t);
      presentationTracks.add(t);
    }

    // Get SMIL catalog
    final SMILDocument smilDocument;
    try {
      smilDocument = SmilUtil.getSmilDocumentFromMediaPackage(mediaPackage, smilFlavor, workspace);
    } catch (SAXException e) {
      throw new WorkflowOperationException(e);
    }
    final SMILParElement parallel = (SMILParElement) smilDocument.getBody().getChildNodes().item(0);
    final NodeList sequences = parallel.getTimeChildren();
    final float trackDurationInSeconds = parallel.getDur();
    final long trackDurationInMs = Math.round(trackDurationInSeconds * 1000f);

    // Define a general Layout for the final video
    // TODO: Find a less hardcoded way to define width and height
    LayoutArea layoutArea = new LayoutArea("webcam", 0, 0, 1920, 1080);

    // Get Start- and endtime of the final video from SMIL
    long finalStartTime = 0;
    long finalEndTime = trackDurationInMs;

    // Create a list of start and stop events, i.e. every time a new video begins or an old one ends
    // Create list from SMIL
    List<StartStopEvent> events = new ArrayList<>();

    for (int i = 0; i < sequences.getLength(); i++) {
      final SMILElement item = (SMILElement) sequences.item(i);
      final VCell<String> sourceType = VCell.cell(EMPTY_VALUE);
      NodeList children = item.getChildNodes();

      for (int j = 0; j < children.getLength(); j++) {
        Node node = children.item(j);
        SMILMediaElement e = (SMILMediaElement) node;

        // Avoid any element that is not a video or of type presenter
        // TODO: Generalize to allow presentation
        if (NODE_TYPE_VIDEO.equals(e.getNodeName())) {
          Track track = getFromOriginal(e.getId(), originalTracks, sourceType);
          if (!sourceType.get().startsWith(PRESENTER_KEY)) {
            continue;
          }
          double beginInSeconds = e.getBegin().item(0).getResolvedOffset();
          long beginInMs = Math.round(beginInSeconds * 1000d);
          double durationInSeconds = e.getDur();
          long durationInMs = Math.round(durationInSeconds * 1000d);

          // Gather video information
          VideoInfo videoInfo = new VideoInfo();
          // Aspect Ratio, e.g. 16:9
          List<Track> tmpList = new ArrayList<Track>();
          tmpList.add(track);
          Dimension trackDimension = determineDimension(tmpList, true);    // TODO?: Make forceDivisible an operation key
          videoInfo.aspectRatioHeight = trackDimension.getHeight();
          videoInfo.aspectRatioWidth = trackDimension.getWidth();

          // TODO: Get track codec, e.g. h264
          videoInfo.codec = "vp8";
          // "StartTime" is calculated laters. Describes how far into the video the next portion starts
          // (E.g. If webcam2 is started 10 seconds after webcam1, the startTime for webcam1 in the next portion is 10)
          videoInfo.startTime = 0;

          logger.info("Video information: Width: {}, Height {}, Codec: {}, StartTime: {}", videoInfo.aspectRatioWidth, videoInfo.aspectRatioHeight, videoInfo.codec, videoInfo.startTime);

          events.add(new StartStopEvent(true, getTrackPath(track), beginInMs, videoInfo));
          events.add(new StartStopEvent(false, getTrackPath(track), beginInMs + durationInMs, videoInfo));
        }
      }
    }

    // Sort by timestamps ascending
    Collections.sort(events);


    // Create an edit decision list
    List<VideoEdl> videoEdl = new ArrayList<VideoEdl>();
    HashMap<String, Long> activeVideos = new HashMap<String, Long>();   // Currently running videos

    // Define starting point
    VideoEdl start = new VideoEdl();
    start.timeStamp = finalStartTime;
    videoEdl.add(start);

    // Define mid-points
    for (StartStopEvent event : events) {
      if (event.start) {
        logger.info("Add start event at {}", event.timeStamp);
        activeVideos.put(event.filename, event.timeStamp);
        videoEdl.add(createVideoEdl(event, activeVideos));
      } else {
        logger.info("Add stop event at {}", event.timeStamp);
        activeVideos.remove(event.filename);
        videoEdl.add(createVideoEdl(event, activeVideos));
      }
    }

    // Define ending point
    VideoEdl endVideo = new VideoEdl();
    endVideo.timeStamp = finalEndTime;
    endVideo.nextTimeStamp = finalEndTime;
    videoEdl.add(endVideo);

    // Pre processing EDL
    for (int i = 0; i < videoEdl.size() - 1; i++) {
      // For calculating cut lengths
      videoEdl.get(i).nextTimeStamp = videoEdl.get(i + 1).timeStamp;
    }

    List<List<String>> commands = new ArrayList<>();

    // Compositing cuts
    for (int i = 0; i < videoEdl.size(); i++) {
      if (videoEdl.get(i).timeStamp == videoEdl.get(i).nextTimeStamp) {
        logger.info("Skipping 0-length edl entry");
        continue;
      }
      commands.add(compositeCut(layoutArea, videoEdl.get(i)));
    }

//    // Create output file path
//    String outputFilePath = FilenameUtils.removeExtension(getTrackPath(presentationTracks.get(0)))
//            .concat('-' + presentationTracks.get(0).getIdentifier()).concat("-multipleWebcams.ts");
//    logger.info("Output file path: " + outputFilePath);
//
//    // Finally run commands
//    Track finalPresenterTrack = runCommands(commands, outputFilePath, targetPresenterFlavor);


    // Create output file path
    String outputFilePath = FilenameUtils.removeExtension(getTrackPath(presentationTracks.get(0)))
            .concat('-' + presentationTracks.get(0).getIdentifier()).concat("-multiplewebcams");
    logger.info("Output file path: " + outputFilePath);

    // Create partial webcam tracks and add them to the mediapackge
    List<Track> finalPresenterTracks = createPartialTracks(commands, outputFilePath, targetPresenterFlavor);

    for (Track finalPresenterTrack : finalPresenterTracks) {
      logger.info("Final track {} got flavor '{}'", finalPresenterTrack, finalPresenterTrack.getFlavor());
      logger.info("Final track hasVideo: {}" , finalPresenterTrack.hasVideo());
      mediaPackage.add(finalPresenterTrack);
    }


    final WorkflowOperationResult result = createResult(mediaPackage, WorkflowOperationResult.Action.CONTINUE, 0);
    logger.debug("Multiple Webcam operation completed");
    return result;
  }

  /**
   * Create a ffmpeg command that generate a videos for the given cutting marks
   * @param layoutArea
   *          General layout information for the video
   *          (Originally it was possible to have multiple layout areas)
   * @param videoEdl
   *          The edit decision list for the current cut
   * @return A command line ready ffmpeg command
   * @throws WorkflowOperationException
   * @throws EncoderException
   */
  private List<String> compositeCut(LayoutArea layoutArea, VideoEdl videoEdl) throws WorkflowOperationException, EncoderException
  {
    // Stuff that should probably be passed to this function
    int width = 1920;
    int height = 1080;

    // Duration for this cut
    long duration = videoEdl.nextTimeStamp - videoEdl.timeStamp;
    logger.info("Cut timeStamp {}, duration {}", videoEdl.timeStamp, duration);

    // Declare ffmpeg command
    String ffmpegFilter = String.format("color=c=white:s=%dx%d:r=24", width, height);

    List<Area> videos = videoEdl.areas;
    int videoCount = videoEdl.areas.size();

    logger.info("Laying out {} videos in {}", videoCount, layoutArea.name);


    if (videoCount > 0) {
      int tilesH = 0;
      int tilesV = 0;
      int tileWidth = 0;
      int tileHeight = 0;
      int totalArea = 0;

      // Do and exhaustive search to maximize video areas
      for (int tmpTilesV = 1; tmpTilesV < videoCount + 1; tmpTilesV++) {
        int tmpTilesH = (int) Math.ceil((videoCount / (float)tmpTilesV));
        int tmpTileWidth = (int) (2 * Math.floor((float)layoutArea.width / tmpTilesH / 2));
        int tmpTileHeight = (int) (2 * Math.floor((float)layoutArea.height / tmpTilesV / 2));

        if (tmpTileWidth <= 0 || tmpTileHeight <= 0) {
          continue;
        }

        int tmpTotalArea = 0;
        for (Area video: videos) {
          int videoWidth = video.aspectRatioWidth;
          int videoHeight = video.aspectRatioHeight;
          VideoInfo videoScaled = aspectScale(videoWidth, videoHeight, tmpTileWidth, tmpTileHeight);
          tmpTotalArea += videoScaled.aspectRatioWidth * videoScaled.aspectRatioHeight;
        }

        if (tmpTotalArea > totalArea) {
          tilesH = tmpTilesH;
          tilesV = tmpTilesV;
          tileWidth = tmpTileWidth;
          tileHeight = tmpTileHeight;
          totalArea = tmpTotalArea;
        }
      }


      int tileX = 0;
      int tileY = 0;

      logger.info("Tiling in a {}x{} grid", tilesH, tilesV);

      ffmpegFilter += String.format("[%s_in];", layoutArea.name);

      for (Area video : videos) {
        //Get videoinfo
        logger.info("tile location ({}, {})", tileX, tileY);
        int videoWidth = video.aspectRatioWidth;
        int videoHeight = video.aspectRatioHeight;
        logger.info("original aspect: {}x{}", videoWidth, videoHeight);

        VideoInfo videoScaled = aspectScale(videoWidth, videoHeight, tileWidth, tileHeight);
        logger.info("scaled size: {}x{}", videoScaled.aspectRatioWidth, videoScaled.aspectRatioHeight);

        Offset offset = padOffset(videoScaled.aspectRatioWidth, videoScaled.aspectRatioHeight, tileWidth, tileHeight);
        logger.info("offset: left: {}, top: {}", offset.x, offset.y);

        logger.info("start timestamp: {}", video.startTime);
        long seekOffset = 0; //video.startTime;  // TODO: Get a proper value instead of the badly hardcoded 0
        logger.info("seek offset: {}", seekOffset);
        logger.info("codec: {}", video.codec);
        //logger.info("duration: {}", video.duration);

        long seek = 0;
        if (video.codec.equals("flashsv2")) {
          // Desktop sharing videos in flashsv2 do not have regular
          // keyframes, so seeking in them doesn't really work.
          // To make processing more reliable, always decode them from the
          // start in each cut. (Slow!)
          seek = 0;
        } else {
          // Webcam videos are variable, low fps; it might be that there's
          // no frame until some time after the seek point. Start decoding
          // 10s before the desired point to avoid this issue.
          seek = video.startTime - 10000;
          if (seek < 0) {
            seek = 0;
          }
        }

        String padName = String.format("%s_x%d_y%d", layoutArea.name, tileX, tileY);

        // Apply the video start time offset to seek to the correct point.
        // Only actually apply the offset if we're already seeking so we
        // don't start seeking in a file where we've overridden the seek
        // behaviour.
        if (seek > 0) {
          seek = seek + seekOffset;
        }
        ffmpegFilter += String.format("movie=%s:sp=%s", video.filename, msToS(seek));
        // Subtract away the offset from the timestamps, so the trimming
        // in the fps filter is accurate
        ffmpegFilter += String.format(",setpts=PTS-%s/TB", msToS(seekOffset));
        // fps filter fills in frames up to the desired start point, and
        // cuts the video there
        ffmpegFilter += String.format(",fps=%d:start_time=%s", FFMPEG_WF_FRAMERATE, msToS(video.startTime));
        // Reset the timestamps to start at 0 so that everything is synced
        // for the video tiling, and scale to the desired size.
        ffmpegFilter += String.format(",setpts=PTS-STARTPTS,scale=%d:%d,setsar=1", videoScaled.aspectRatioWidth, videoScaled.aspectRatioHeight);
        // And finally, pad the video to the desired aspect ratio
        ffmpegFilter += String.format(",pad=w=%d:h=%d:x=%d:y=%d:color=white", tileWidth, tileHeight, offset.x, offset.y);
        ffmpegFilter += String.format("[%s_movie];", padName);

        // In case the video was shorter than expected, we might have to pad
        // it to length. do that by concatenating a video generated by the
        // color filter. (It would be nice to repeat the last frame instead,
        // but there's no easy way to do that.)
        ffmpegFilter += String.format("color=c=white:s=%dx%d:r=%d", tileWidth, tileHeight, FFMPEG_WF_FRAMERATE);
        ffmpegFilter += String.format("[%s_pad];", padName);
        ffmpegFilter += String.format("[%s_movie][%s_pad]concat=n=2:v=1:a=0[%s];", padName, padName, padName);

        tileX += 1;
        if (tileX >= tilesH) {
          tileX = 0;
          tileY += 1;
        }
      }

      // Create the video rows
      int remaining = videoCount;
      for (tileY = 0; tileY < tilesV; tileY++) {
        int thisTilesH = Math.min(tilesH, remaining);
        remaining -= thisTilesH;

        for (tileX = 0; tileX < thisTilesH; tileX++) {
          ffmpegFilter += String.format("[%s_x%d_y%d]", layoutArea.name, tileX, tileY);
        }
        if (thisTilesH > 1) {
          ffmpegFilter += String.format("hstack=inputs=%d,", thisTilesH);
        }
        ffmpegFilter += String.format("pad=w=%d:h=%d:color=white", layoutArea.width, tileHeight);
        ffmpegFilter += String.format("[%s_y%d];", layoutArea.name, tileY);
      }

      // Stack the video rows
      for (tileY = 0; tileY < tilesV; tileY++) {
        ffmpegFilter += String.format("[%s_y%d]", layoutArea.name, tileY);
      }
      if (tilesV > 1) {
        ffmpegFilter += String.format("vstack=inputs=%d,", tilesV);
      }
      ffmpegFilter += String.format("pad=w=%d:h=%d:color=white", layoutArea.width, layoutArea.height);
      ffmpegFilter += String.format("[%s];", layoutArea.name);
      ffmpegFilter += String.format("[%s_in][%s]overlay=x=%d:y=%d", layoutArea.name, layoutArea.name, layoutArea.x, layoutArea.y);

    // Here would be the end of the layoutArea Loop
    }

    ffmpegFilter += String.format(",trim=end=%s", msToS(duration));

    List<String> ffmpegCmd = new ArrayList<String>(Arrays.asList(FFMPEG));
    ffmpegCmd.add("-filter_complex");
    ffmpegCmd.add(ffmpegFilter);
    ffmpegCmd.addAll(Arrays.asList(FFMPEG_WF_ARGS));
    //ffmpegCmd.add("-");

    logger.info("Final command:");
    logger.info(String.join(" ", ffmpegCmd));

    return ffmpegCmd;
  }

  /** NOW UNUSED
   * DUE TO PROBLEMS WITH THE FINAL FILE, WHICH COULD NOT BE FURTHER PROCESSED BY OTHER ENCODING OPERATIONS
   * Runs multiple ffmpeg commands, and pipes their output over stdout into the same single file
   * Then creates a track out of the final file
   */
  private Track runCommands(List<List<String>> commands, String outputFilePath, MediaPackageElementFlavor flavor) throws WorkflowOperationException, EncoderException {
    String waveformFilePath = outputFilePath;

    File outputFile = new File(waveformFilePath);

    for (List<String> command : commands) {
      command.add("-f");
      command.add("mpgets");
      command.add("-");
      logger.info("Running command: {}", command);

      // run ffmpeg
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outputFile));
      Process ffmpegProcess = null;
      int exitCode = 1;
      BufferedReader errStream = null;
      try {
        ffmpegProcess = pb.start();


        errStream = new BufferedReader(new InputStreamReader(ffmpegProcess.getInputStream()));
        String line = errStream.readLine();
        while (line != null) {
          logger.info(line);
          line = errStream.readLine();
        }

        exitCode = ffmpegProcess.waitFor();
      } catch (IOException ex) {
        throw new WorkflowOperationException("Start ffmpeg process failed", ex);
      } catch (InterruptedException ex) {
        throw new WorkflowOperationException("Waiting for encoder process exited was interrupted unexpectedly", ex);
      } finally {
        IoSupport.closeQuietly(ffmpegProcess);
        IoSupport.closeQuietly(errStream);
        if (exitCode != 0) {
          try {
            logger.warn("FFMPEG process exited with errorcode: " + exitCode);
            FileUtils.forceDelete(new File(waveformFilePath));
          } catch (IOException e) {
            // it is ok, no output file was generated by ffmpeg
          }
        }
      }

      if (exitCode != 0)
        throw new WorkflowOperationException(String.format("The encoder process exited abnormally with exit code %s "
                + "using command\n%s", exitCode, String.join(" ", command)));
    }


    // put waveform image into workspace
    FileInputStream waveformFileInputStream = null;
    URI waveformFileUri;
    try {
      waveformFileInputStream = new FileInputStream(waveformFilePath);
      waveformFileUri = workspace.putInCollection("multipleWebcams",
              FilenameUtils.getName(waveformFilePath), waveformFileInputStream);
      logger.info("Copied the created waveform to the workspace {}", waveformFileUri);
    } catch (FileNotFoundException ex) {
      throw new WorkflowOperationException(String.format("Waveform image file '%s' not found", waveformFilePath), ex);
    } catch (IOException ex) {
      throw new WorkflowOperationException(String.format(
              "Can't write waveform image file '%s' to workspace", waveformFilePath), ex);
    } catch (IllegalArgumentException ex) {
      throw new WorkflowOperationException(ex);
    } finally {
      IoSupport.closeQuietly(waveformFileInputStream);
//      logger.info("Deleted local waveform image file at {}", waveformFilePath);
//      FileUtils.deleteQuietly(new File(waveformFilePath));
    }

    // create media package element
    MediaPackageElementBuilder mpElementBuilder = MediaPackageElementBuilderFactory.newInstance().newElementBuilder();
    // it is up to the workflow operation handler to set the attachment flavor
    Track waveformMpe = (Track) mpElementBuilder.elementFromURI(
            waveformFileUri, MediaPackageElement.Type.Track, flavor);
    waveformMpe.setIdentifier(IdBuilderFactory.newInstance().newIdBuilder().createNew().compact());

    return waveformMpe;
  }

  /**
   * Runs multiple ffmpeg commands. Saves each output in workspace with enumerated filenames
   * TODO: FIGURE OUT WHY THE PARTIAL TRACKS LACK VITAL METADATA
   * TODO: CONCATENATE PARTIAL TRACKS RIGHT HERE INSTEAD OF IN LATER OPERATIONS
   * @param commands
   *          Fully qualified ffmpeg commands EXCEPT for the output file
   * @param outputFilePath
   *          Path to location where enumerated files can be stored.
   * @param flavor
   *          Output flavor
   * @return A list of partial tracks
   * @throws WorkflowOperationException
   * @throws EncoderException
   */
  private List<Track> createPartialTracks(List<List<String>> commands, String outputFilePath, MediaPackageElementFlavor flavor) throws WorkflowOperationException, EncoderException {
    String waveformFilePath = outputFilePath;

    List<String> outputPaths = new ArrayList<>();
    int index = 0;
    for (List<String> command : commands) {
      String outputFile = outputFilePath + "part" + index + ".mp4";
      outputPaths.add(outputFile);

      command.add(outputFile);
      index++;

      logger.info("Running command: {}", command);

      // Run ffmpeg
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process ffmpegProcess = null;
      int exitCode = 1;
      BufferedReader errStream = null;
      try {
        ffmpegProcess = pb.start();

        errStream = new BufferedReader(new InputStreamReader(ffmpegProcess.getInputStream()));
        String line = errStream.readLine();
        while (line != null) {
          logger.info(line);
          line = errStream.readLine();
        }

        exitCode = ffmpegProcess.waitFor();
      } catch (IOException ex) {
        throw new WorkflowOperationException("Start ffmpeg process failed", ex);
      } catch (InterruptedException ex) {
        throw new WorkflowOperationException("Waiting for encoder process exited was interrupted unexpectedly", ex);
      } finally {
        IoSupport.closeQuietly(ffmpegProcess);
        IoSupport.closeQuietly(errStream);
        if (exitCode != 0) {
          try {
            logger.warn("FFMPEG process exited with errorcode: " + exitCode);
            FileUtils.forceDelete(new File(waveformFilePath));
          } catch (IOException e) {
            // it is ok, no output file was generated by ffmpeg
          }
        }
      }

      if (exitCode != 0)
        throw new WorkflowOperationException(String.format("The encoder process exited abnormally with exit code %s "
                + "using command\n%s", exitCode, String.join(" ", command)));
    }

    List<Track> tracks = new ArrayList<Track>();
    for (String outputPath : outputPaths) {

      // Put webcam video into workspace
      // Because tracks require an URI, and I don't know how else to get one
      FileInputStream outputFileInputStream = null;
      URI webcamFileUri;
      try {
        outputFileInputStream = new FileInputStream(outputPath);
        webcamFileUri = workspace.putInCollection("multipleWebcams",
                FilenameUtils.getName(outputPath), outputFileInputStream);
        logger.info("Copied the created webcam video to the workspace {}", webcamFileUri);
      } catch (FileNotFoundException ex) {
        throw new WorkflowOperationException(String.format("Webcam file '%s' not found", outputPath), ex);
      } catch (IOException ex) {
        throw new WorkflowOperationException(String.format(
                "Can't write webcam file '%s' to workspace", outputPath), ex);
      } catch (IllegalArgumentException ex) {
        throw new WorkflowOperationException(ex);
      } finally {
        IoSupport.closeQuietly(outputFileInputStream);

//        logger.info("Deleted local webcam video file at {}", outputPath);
//        FileUtils.deleteQuietly(new File(outputPath));
      }

      // Create media package element
      // So that the tracks can be properly added to a mediapackage
      MediaPackageElementBuilder mpElementBuilder = MediaPackageElementBuilderFactory.newInstance().newElementBuilder();
      Track t = (Track) mpElementBuilder.elementFromURI(
              webcamFileUri, MediaPackageElement.Type.Track, flavor);
      t.setIdentifier(IdBuilderFactory.newInstance().newIdBuilder().createNew().compact());

      tracks.add(t);
    }

    return tracks;

    // Test attempt concatenating the single tracks right here.
    // Concat operation fails because it cannot find the tracks at their URI. Probably a problem with the missing metadata
//    Track finalTrack = new TrackImpl();
//
//    try {
//      Job job = composerService.concat(composerService.getProfile("concat.work").getIdentifier(), new Dimension(1920, 1080), false, org.opencastproject.util.data.Collections.toArray(Track.class, tracks));
//
//      if (!JobUtil.waitForJob(serviceRegistry, job).isSuccess()) {
//        throw new WorkflowOperationException("At least one of the jobs did not complete successfully");
//      }
//
//      final Opt<Job> concatJob = JobUtil.update(serviceRegistry, job);
//      if (concatJob.isSome()) {
//        final String concatPayload = concatJob.get().getPayload();
//        if (concatPayload != null) {
//          final Track concatTrack;
//          try {
//            concatTrack = (Track) MediaPackageElementParser.getFromXml(concatPayload);
//          } catch (MediaPackageException e) {
//            throw new WorkflowOperationException(e);
//          }
//
//          final String fileName = PRESENTER_KEY;
//
//          concatTrack.setFlavor(flavor);
//
//          concatTrack.setURI(workspace
//                  .moveTo(concatTrack.getURI(), mediaPackage.getIdentifier().toString(), concatTrack.getIdentifier(),
//                          fileName + "." + FilenameUtils.getExtension(concatTrack.getURI().toString())));
//
//          logger.info("Concatenated track {} got flavor '{}'", concatTrack, concatTrack.getFlavor());
//
//          finalTrack = concatTrack;
//        }
//      }
//    } catch (MediaPackageException e) {
//      throw new WorkflowOperationException(e);
//    } catch (NotFoundException e) {
//      throw new WorkflowOperationException(e);
//    } catch (IOException e) {
//      throw new WorkflowOperationException(e);
//    } catch (ServiceRegistryException e) {
//      throw new WorkflowOperationException(e);
//    }
//
//    return finalTrack;
  }


  private VideoInfo aspectScale(int oldWidth, int oldHeight, int newWidth, int newHeight) {
    if ((float)oldWidth / oldHeight > (float)newWidth / newHeight) {
      newHeight = (int) (2 * Math.round((float)oldHeight * newWidth / oldWidth / 2));
    } else {
      newWidth = (int) (2 * Math.round((float)oldWidth * newHeight / oldHeight / 2));
    }
    return new VideoInfo(newHeight, newWidth);
  }

  private Offset padOffset(int videoWidth, int videoHeight, int areaWidth, int areaHeight) {
      int padX = (int) (2 * Math.round((float)(areaWidth - videoWidth) / 4));
      int padY = (int) (2 * Math.round((float)(areaHeight - videoHeight) / 4));
      return new Offset(padX, padY);
  }

  private String msToS(long timestamp)
  {
    double s = (double)timestamp / 1000;
    //double ms = timestamp % 1000;
    return String.format(Locale.US, "%.3f", s);   // Locale.US to get a . instead of a ,
  }

  private Track getFromOriginal(String trackId, List<Track> originalTracks, VCell<String> type) {
    for (Track t : originalTracks) {
      if (t.getIdentifier().contains(trackId)) {
        logger.debug("Track-Id from smil found in Mediapackage ID: " + t.getIdentifier());
        if (EMPTY_VALUE.equals(type.get())) {
          String suffix = (t.hasAudio() && !t.hasVideo()) ? FLAVOR_AUDIO_SUFFIX : "";
          type.set(t.getFlavor().getType() + suffix);
        }
        originalTracks.remove(t);
        return t;
      }
    }
    throw new IllegalStateException("No track matching smil Track-id: " + trackId);
  }


  /**
   * @param flavorType
   *          either "presenter" or "presentation", just for error messages
   */
  private MediaPackageElementFlavor parseTargetFlavor(String flavor, String flavorType)
          throws WorkflowOperationException {
    final MediaPackageElementFlavor targetFlavor;
    try {
      targetFlavor = MediaPackageElementFlavor.parseFlavor(flavor);
      if ("*".equals(targetFlavor.getType()) || "*".equals(targetFlavor.getSubtype())) {
        throw new WorkflowOperationException(format(
                "Target %s flavor must have a type and a subtype, '*' are not allowed!", flavorType));
      }
    } catch (IllegalArgumentException e) {
      throw new WorkflowOperationException(format("Target %s flavor '%s' is malformed", flavorType, flavor));
    }
    return targetFlavor;
  }

  /** Create a derived audio flavor by appending {@link #FLAVOR_AUDIO_SUFFIX} to the flavor type. */
  private MediaPackageElementFlavor deriveAudioFlavor(MediaPackageElementFlavor flavor) {
    return MediaPackageElementFlavor.flavor(flavor.getType().concat(FLAVOR_AUDIO_SUFFIX), flavor.getSubtype());
  }

  private TrackSelector mkTrackSelector(Opt<String> flavor) throws WorkflowOperationException {
    final TrackSelector s = new TrackSelector();
    for (String fs : flavor) {
      try {
        final MediaPackageElementFlavor f = MediaPackageElementFlavor.parseFlavor(fs);
        s.addFlavor(f);
        s.addFlavor(deriveAudioFlavor(f));
      } catch (IllegalArgumentException e) {
        throw new WorkflowOperationException("Flavor '" + fs + "' is malformed");
      }
    }
    return s;
  }

  /**
   * Determine the largest dimension of the given list of tracks
   *
   * @param tracks
   *          the list of tracks
   * @param forceDivisible
   *          Whether to enforce the track's dimension to be divisible by two
   * @return the largest dimension from the list of track
   */
  private Dimension determineDimension(List<Track> tracks, boolean forceDivisible) {
    Tuple<Track, Dimension> trackDimension = getLargestTrack(tracks);
    if (trackDimension == null)
      return null;

    if (forceDivisible && (trackDimension.getB().getHeight() % 2 != 0 || trackDimension.getB().getWidth() % 2 != 0)) {
      Dimension scaledDimension = Dimension.dimension((trackDimension.getB().getWidth() / 2) * 2, (trackDimension
              .getB().getHeight() / 2) * 2);
      logger.info("Determined output dimension {} scaled down from {} for track {}", scaledDimension,
              trackDimension.getB(), trackDimension.getA());
      return scaledDimension;
    } else {
      logger.info("Determined output dimension {} for track {}", trackDimension.getB(), trackDimension.getA());
      return trackDimension.getB();
    }
  }

  /**
   * Returns the track with the largest resolution from the list of tracks
   *
   * @param tracks
   *          the list of tracks
   * @return a {@link Tuple} with the largest track and it's dimension
   */
  private Tuple<Track, Dimension> getLargestTrack(List<Track> tracks) {
    Track track = null;
    Dimension dimension = null;
    for (Track t : tracks) {
      if (!t.hasVideo())
        continue;

      VideoStream[] videoStreams = TrackSupport.byType(t.getStreams(), VideoStream.class);
      int frameWidth = videoStreams[0].getFrameWidth();
      int frameHeight = videoStreams[0].getFrameHeight();
      if (dimension == null || (frameWidth * frameHeight) > (dimension.getWidth() * dimension.getHeight())) {
        dimension = Dimension.dimension(frameWidth, frameHeight);
        track = t;
      }
    }
    if (track == null || dimension == null)
      return null;

    return Tuple.tuple(track, dimension);
  }

  /**
   * Returns the absolute path of the track
   *
   * @param track
   *          Track whose path you want
   * @return {@String} containing the absolute path of the given track
   * @throws WorkflowOperationException
   */
  private String getTrackPath(Track track) throws WorkflowOperationException {
    File mediaFile;
    try {
      mediaFile = workspace.get(track.getURI());
    } catch (NotFoundException e) {
      throw new WorkflowOperationException(
              "Error finding the media file in the workspace", e);
    } catch (IOException e) {
      throw new WorkflowOperationException(
              "Error reading the media file in the workspace", e);
    }

    String filePath = mediaFile.getAbsolutePath();
    return filePath;
  }

  private VideoEdl createVideoEdl(StartStopEvent event, HashMap<String, Long> activeVideos) {
    VideoEdl nextEdl = new VideoEdl();
    nextEdl.timeStamp = event.timeStamp;

    for (Map.Entry<String, Long> filename : activeVideos.entrySet()) {
      nextEdl.areas.add(new Area(filename.getKey(), event.timeStamp, event.videoInfo.aspectRatioHeight, event.videoInfo.aspectRatioWidth, event.videoInfo.codec, event.timeStamp - filename.getValue()));
    }

    return nextEdl;
  }
}
