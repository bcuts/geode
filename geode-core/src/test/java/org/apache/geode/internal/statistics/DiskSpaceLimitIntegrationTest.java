/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.internal.statistics;

import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

import org.apache.geode.StatisticDescriptor;
import org.apache.geode.Statistics;
import org.apache.geode.StatisticsType;
import org.apache.geode.internal.NanoTimer;
import org.apache.geode.test.junit.categories.IntegrationTest;

@Category(IntegrationTest.class)
public class DiskSpaceLimitIntegrationTest {

  private static final long FILE_SIZE_LIMIT = 1024 * 1;
  private static final long DISK_SPACE_LIMIT = Long.MAX_VALUE;

  private File dir;
  private String archiveFileName;

  private LocalStatisticsFactory factory;
  private StatisticDescriptor[] statisticDescriptors;
  private StatisticsType statisticsType;
  private Statistics statistics;

  private SampleCollector sampleCollector;
  private StatArchiveHandlerConfig config;

  @Rule
  public RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule
  public TestName testName = new TestName();

  @Before
  public void setUp() throws Exception {
    this.dir = this.temporaryFolder.getRoot();
    this.archiveFileName =
        new File(this.dir, this.testName.getMethodName() + ".gfs").getAbsolutePath();

    this.factory = new LocalStatisticsFactory(null);
    this.statisticDescriptors = new StatisticDescriptor[] {
        this.factory.createIntCounter("stat1", "description of stat1", "units", true),};
    this.statisticsType =
        factory.createType("statisticsType1", "statisticsType1", this.statisticDescriptors);
    this.statistics = factory.createAtomicStatistics(this.statisticsType, "statistics1", 1);

    StatisticsSampler sampler = mock(StatisticsSampler.class);
    when(sampler.getStatistics()).thenReturn(this.factory.getStatistics());

    this.config = mock(StatArchiveHandlerConfig.class);
    when(this.config.getArchiveFileName()).thenReturn(new File(this.archiveFileName));
    when(this.config.getArchiveFileSizeLimit()).thenReturn(FILE_SIZE_LIMIT);
    when(this.config.getSystemId()).thenReturn(1L);
    when(this.config.getSystemStartTime()).thenReturn(System.currentTimeMillis());
    when(this.config.getSystemDirectoryPath())
        .thenReturn(this.temporaryFolder.getRoot().getAbsolutePath());
    when(this.config.getProductDescription()).thenReturn(this.testName.getMethodName());
    when(this.config.getArchiveDiskSpaceLimit()).thenReturn(DISK_SPACE_LIMIT);

    this.sampleCollector = new SampleCollector(sampler);
    this.sampleCollector.initialize(this.config, NanoTimer.getTime());
  }

  @After
  public void tearDown() throws Exception {
    StatisticsTypeFactoryImpl.clear();
  }

  @Test
  public void zeroKeepsAllFiles() throws Exception {
    when(this.config.getArchiveDiskSpaceLimit()).thenReturn(0L);
    sampleUntilFileExists(archiveFile(1));
    sampleUntilFileExists(archiveFile(2));
    assertThat(archiveFile(1)).exists();
    assertThat(archiveFile(2)).exists();
  }

  @Test
  public void sameKeepsOneFile() throws Exception {
    when(this.config.getArchiveDiskSpaceLimit()).thenReturn(FILE_SIZE_LIMIT * 2);
    sampleUntilFileExists(archiveFile(1));
    sampleUntilFileExists(archiveFile(2));
    assertThat(archiveFile(1)).doesNotExist();
    assertThat(archiveFile(2)).exists();
  }

  private File archiveFile(final int child) {
    return new File(this.dir,
        this.testName.getMethodName() + "-01-" + String.format("%02d", child) + ".gfs");
  }

  private void sampleUntilFileExists(final File file)
      throws InterruptedException, TimeoutException {
    long end = System.nanoTime() + MINUTES.toNanos(1);
    while (!file.exists() && System.nanoTime() < end) {
      sample();
    }
    if (!file.exists()) {
      throw new TimeoutException("File " + file + " does not exist within " + 1 + " " + MINUTES);
    }
  }

  private void sample() {
    getSampleCollector().sample(System.nanoTime());
  }

  private SampleCollector getSampleCollector() {
    return this.sampleCollector;
  }
}