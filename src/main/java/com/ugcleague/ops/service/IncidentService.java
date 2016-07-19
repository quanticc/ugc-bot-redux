package com.ugcleague.ops.service;

import com.ugcleague.ops.domain.document.Incident;
import com.ugcleague.ops.event.IncidentCreatedEvent;
import com.ugcleague.ops.repository.mongo.IncidentRepository;
import com.ugcleague.ops.service.discord.AnnouncePresenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);
    public static final String DISCORD_RESTART = "discord.restarts";

    private final IncidentRepository incidentRepository;
    private final AnnouncePresenter announcePresenter;

    @Autowired
    public IncidentService(IncidentRepository incidentRepository, AnnouncePresenter announcePresenter) {
        this.incidentRepository = incidentRepository;
        this.announcePresenter = announcePresenter;
    }

    public Optional<Incident> getLastIncidentFromGroup(String group) {
        List<Incident> incidents = incidentRepository.findByGroup(group);
        if (incidents.isEmpty()) {
            return Optional.empty();
        } else if (incidents.size() == 1) {
            return Optional.of(incidents.get(0));
        } else {
            incidents.sort((o1, o2) -> o1.getCreatedDate().compareTo(o2.getCreatedDate()));
            return Optional.of(incidents.get(incidents.size() - 1));
        }
    }

    @EventListener
    public void onIncidentCreated(IncidentCreatedEvent event) {
        Incident incident = event.getSource();
        incident = incidentRepository.save(incident);
        log.debug("New incident: {}", incident);
        announcePresenter.announce("incidents.new", incident.getName());
    }

    public List<Incident> getAllIncidents() {
        return incidentRepository.findAll();
    }
}
