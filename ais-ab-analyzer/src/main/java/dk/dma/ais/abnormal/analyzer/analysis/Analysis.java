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

package dk.dma.ais.abnormal.analyzer.analysis;

import dk.dma.ais.abnormal.event.db.EventRepository;
import dk.dma.ais.abnormal.event.db.domain.Event;
import dk.dma.ais.abnormal.event.db.domain.builders.TrackingPointBuilder;
import dk.dma.ais.abnormal.tracker.Track;
import dk.dma.ais.abnormal.tracker.TrackingService;
import dk.dma.enav.model.geometry.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * An Analysis is a class which is known to the ais-ab-analyzer application and possesses certain public
 * methods which can be called to analyze and detect events.
 *
 * The Analysis class is an abstract class which all analyses must inherit from.
 *
 * The Analysis class provides basic methods to its subclasses, so they can reuse the code to raise and
 * lower events.
 *
 */
public abstract class Analysis {

    private static final Logger LOG = LoggerFactory.getLogger(Analysis.class);

    private final EventRepository eventRepository;
    private final TrackingService trackingService;

    protected Analysis(EventRepository eventRepository, TrackingService trackingService) {
        this.eventRepository = eventRepository;
        this.trackingService = trackingService;
    }

    /**
     * The analysis will only start to receive trackEvents from the trackingService once this start() method
     * has been called.
     */
    public void start() {
        LOG.info(this.getClass().getSimpleName() + " starts to listen for tracking events.");
        trackingService.registerSubscriber(this);
    }

    /**
     * This abstract method is intended to be implemented by subclasses, so that they can build
     * and return the proper Event entity when a new event is raised.
     *
     * @param track
     * @return
     */
    protected abstract Event buildEvent(Track track);

    /**
     * If an event of the given type and involving the given track has already been raised, then lower it.
     * @param track
     */
    protected void lowerExistingAbnormalEventIfExists(Class<? extends Event> eventClass, Track track) {
        Integer mmsi = track.getMmsi();
        Event ongoingEvent = eventRepository.findOngoingEventByVessel(mmsi, eventClass);
        if (ongoingEvent != null) {
            Date timestamp = new Date((Long) track.getProperty(Track.TIMESTAMP_ANY_UPDATE));
            ongoingEvent.setState(Event.State.PAST);
            ongoingEvent.setEndTime(timestamp);
            eventRepository.save(ongoingEvent);
        }
    }

    /**
     * Raise a new event of type eventClass for the given track. If such an event has already been raised then
     * maintain it and add the tracks newest behaviour to it.
     *
     * @param eventClass
     * @param track
     */
    protected void raiseOrMaintainAbnormalEvent(Class<? extends Event> eventClass, Track track) {
        Integer mmsi = track.getMmsi();
        Event event = eventRepository.findOngoingEventByVessel(mmsi, eventClass);

        if (event != null) {
            Date positionTimestamp = new Date((Long) track.getProperty(Track.TIMESTAMP_POSITION_UPDATE));
            Position position = (Position) track.getProperty(Track.POSITION);
            Float cog = (Float) track.getProperty(Track.COURSE_OVER_GROUND);
            Float sog = (Float) track.getProperty(Track.SPEED_OVER_GROUND);
            Boolean interpolated = (Boolean) track.getProperty(Track.POSITION_IS_INTERPOLATED);

            event.getBehaviour().addTrackingPoint(
                TrackingPointBuilder.TrackingPoint()
                    .timestamp(positionTimestamp)
                    .positionInterpolated(interpolated)
                    .speedOverGround(sog)
                    .courseOverGround(cog)
                    .latitude(position.getLatitude())
                    .longitude(position.getLongitude())
                .getTrackingPoint()
            );
        } else {
            event = buildEvent(track);
        }

        eventRepository.save(event);
    }

    protected EventRepository getEventRepository() {
        return eventRepository;
    }

    protected TrackingService getTrackingService() {
        return trackingService;
    }
}
