/*
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


package org.opencastproject.composer.api;

import java.util.List;
import java.util.Map;

/**
 * An encoding format encapsulates all the relevant configuration data for
 * encoding a media file to a certain encoding formats.
 */
public interface EncodingProfile {

  /**
   * Returns the unique format identifier.
   *
   * @return the format identifier
   */
  String getIdentifier();

  /**
   * Returns the encoding format's name.
   *
   * @return the format name
   */
  String getName();


  /**
   * Returns the source object that provided this encoding profile
   *
   * @return the source object that provided this profile
   */
  Object getSource();

  /**
   * Returns a suffix of the files. First tag found used if tags are used but not provided in the request
   *
   * @return the suffix
   */
  String getSuffix();

  /**
   * Returns a suffix of the files for a certain tag.
   *
   * @param tag a tag that describes the aoutput file
   * @return the suffix
   */
  String getSuffix(String tag);

  /**
   * Returns a list of the tags for output files used in this request
   * @return a list of the used tags
   */
  List<String> getTags();

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.composer.api.EncodingProfile#getMimeType()
   */
  String getMimeType();

  /**
   * Sets the Mimetype.
   *
   * @param mimeType
   *          the Mimetype
   */
  void setMimeType(String mimeType);

  /**
   * Returns <code>true</code> if additional properties have been specified.
   *
   * @return <code>true</code> if there are additional properties
   */
  boolean hasExtensions();

  /**
   * Returns the extension specified by <code>key</code> or <code>null</code> if
   * no such key was defined.
   * <p>
   * Note that <code>key</code> must not contain the media format prefix, so if
   * the configured entry was <code>mediaformat.format.xyz.test</code>, then the key
   * to access the value must simply be <code>test</code>.
   * </p>
   *
   * @param key
   *          the extension key
   * @return the value or <code>null</code>
   */
  String getExtension(String key);

  /**
   * Returns a map containing the additional properties or an empty map if no
   * additional properties were found.
   *
   * @return the additional properties
   */
  Map<String, String> getExtensions();

  /**
   * Returns an estimate of the load a single job with this profile causes.
   * This should be roughly equal to the number of processor cores used at runtime.
   *
   * @return the load a single job with this profile causes
   */
  float getJobLoad();
}
