/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */

package dk.dma.ais.abnormal.analyzer;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import dk.dma.ais.abnormal.event.db.EventRepository;
import dk.dma.ais.abnormal.event.db.h2.H2EventRepository;
import dk.dma.ais.abnormal.stat.db.FeatureDataRepository;
import dk.dma.ais.abnormal.stat.db.data.DatasetMetaData;
import dk.dma.ais.abnormal.stat.db.data.FeatureData2Key;
import dk.dma.ais.abnormal.stat.db.mapdb.FeatureDataRepositoryMapDB;
import dk.dma.ais.abnormal.tracker.TrackingService;
import dk.dma.ais.abnormal.tracker.TrackingServiceImpl;
import dk.dma.ais.reader.AisReader;
import dk.dma.ais.reader.AisReaders;
import dk.dma.enav.model.geometry.grid.Grid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public final class AbnormalAnalyzerAppModule extends AbstractModule {
    private static final Logger LOG = LoggerFactory.getLogger(AbnormalAnalyzerAppModule.class);
    {
        LOG.info(this.getClass().getSimpleName() + " created (" + this + ").");
    }

    private final String inputDirectory;
    private final String inputFilenamePattern;
    private final boolean inputRecursive;
    private final String featureData;

    public AbnormalAnalyzerAppModule(String inputDirectory, String inputFilenamePattern, boolean inputRecursive, String featureData) {
        this.inputDirectory = inputDirectory;
        this.inputFilenamePattern = inputFilenamePattern;
        this.inputRecursive = inputRecursive;
        this.featureData = featureData;
    }

    @Override
    public void configure() {
        bind(AbnormalAnalyzerApp.class).in(Singleton.class);
        bind(AppStatisticsService.class).to(AppStatisticsServiceImpl.class).in(Singleton.class);
        bind(PacketHandler.class).to(PacketHandlerImpl.class).in(Singleton.class);
        bind(TrackingService.class).to(TrackingServiceImpl.class).in(Singleton.class);
        bind(EventRepository.class).to(H2EventRepository.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    FeatureDataRepository provideFeatureDataRepository() {
        FeatureDataRepository featureDataRepository = null;
        try {
            String featureDataFilename = featureData;
            featureDataRepository = new FeatureDataRepositoryMapDB(featureDataFilename);
            featureDataRepository.openForRead();
            LOG.info("Opened feature set database with filename '" + featureDataFilename + "' for read.");
            if (!isValidFeatureDataRepositoryFormat(featureDataRepository)) {
                LOG.error("Feature data repository is invalid. Analyses will be unreliable!");
            } else {
                LOG.info("Feature data repository is valid.");
            }
        } catch (Exception e) {
            LOG.error("Failed to create or open FeatureDataRepository.", e);
        }
        return featureDataRepository;
    }

    @Provides
    @Singleton
    AisReader provideAisReader() {
        AisReader aisReader = null;
        try {
            aisReader = AisReaders.createDirectoryReader(inputDirectory, inputFilenamePattern, inputRecursive);
            LOG.info("Created AisReader (" + aisReader + ").");
        } catch (Exception e) {
            LOG.error("Failed to create AisReader.", e);
        }
        return aisReader;
    }

    @Provides
    @Singleton
    Grid provideGrid() {
        Grid grid = null;
        try {
            FeatureDataRepository featureDataRepository = AbnormalAnalyzerApp.getInjector().getInstance(FeatureDataRepository.class);
            DatasetMetaData metaData = featureDataRepository.getMetaData();
            Double gridResolution = metaData.getGridResolution();
            grid = Grid.create(gridResolution);
            LOG.info("Created Grid with size " + grid.getSize() + " meters.");
        } catch (Exception e) {
            LOG.error("Failed to create Grid object", e);
        }
        return grid;
    }

    private static boolean isValidFeatureDataRepositoryFormat(FeatureDataRepository featureDataRepository) {
        boolean valid = true;

        // TODO Check format version no.

        // Ensure that all expected features are present in the feature file
        boolean containsFeatureShipSizeAndTypeFeature = false;
        Set<String> featureNames = featureDataRepository.getFeatureNames();
        for (String featureName : featureNames) {
            if ("ShipTypeAndSizeFeature".equals(featureName)) {
                containsFeatureShipSizeAndTypeFeature = true;
            }
        }

        if (!containsFeatureShipSizeAndTypeFeature) {
            LOG.error("Feature data do not contain data for feature \"ShipTypeAndSizeFeature\"");
            valid = false;
        }

        // Check ShipTypeAndSizeFeature
        FeatureData2Key shipSizeAndTypeFeature = (FeatureData2Key) featureDataRepository.getFeatureDataForRandomCell("ShipTypeAndSizeFeature");

        if (! "FeatureData2Key".equals(shipSizeAndTypeFeature.getFeatureDataType())) {
            LOG.error("ShipTypeAndSizeFeature: Not stored in the expected format. Cannot analyse against this feature.");
            valid = false;
        }

        String meaningOfKey1 = shipSizeAndTypeFeature.getMeaningOfKey1();
        String meaningOfKey2 = shipSizeAndTypeFeature.getMeaningOfKey2();
        String expectedMeaningOfKey1 = "shipType";
        String expectedMeaningOfKey2 = "shipSize";
        if (! expectedMeaningOfKey1.equals(meaningOfKey1)) {
            LOG.error("ShipTypeAndSizeFeature: Meaning of key 1 is " + meaningOfKey1 + "; not " + expectedMeaningOfKey1 + " as expected. Cannot analyse against this feature.");
            valid = false;
        }
        if (! expectedMeaningOfKey2.equals(meaningOfKey2)) {
            LOG.error("ShipTypeAndSizeFeature: Meaning of key 2 is " + meaningOfKey2 + "; not " + expectedMeaningOfKey2 + " as expected. Cannot analyse against this feature.");
            valid = false;
        }

        return valid;
    }

}