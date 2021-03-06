/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.environment.server;

import com.google.common.collect.Lists;

import org.eclipse.che.api.core.rest.ServiceContext;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.api.core.rest.shared.dto.LinkParameter;
import org.eclipse.che.api.machine.shared.Constants;
import org.eclipse.che.api.machine.shared.dto.MachineDto;
import org.eclipse.che.api.machine.shared.dto.MachineProcessDto;
import org.eclipse.che.api.machine.shared.dto.ServerDto;

import javax.inject.Singleton;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.eclipse.che.api.core.util.LinksHelper.createLink;
import static org.eclipse.che.api.machine.shared.Constants.ENVIRONMENT_OUTPUT_CHANNEL_TEMPLATE;
import static org.eclipse.che.api.machine.shared.Constants.ENVIRONMENT_STATUS_CHANNEL_TEMPLATE;
import static org.eclipse.che.api.machine.shared.Constants.EXEC_AGENT_REFERENCE;
import static org.eclipse.che.api.machine.shared.Constants.LINK_REL_ENVIRONMENT_OUTPUT_CHANNEL;
import static org.eclipse.che.api.machine.shared.Constants.TERMINAL_REFERENCE;
import static org.eclipse.che.dto.server.DtoFactory.cloneDto;
import static org.eclipse.che.dto.server.DtoFactory.newDto;

/**
 * Helps to inject {@link MachineService} related links.
 *
 * @author Anton Korneta
 */
@Singleton
public class MachineServiceLinksInjector {

    public MachineDto injectLinks(MachineDto machine, ServiceContext serviceContext) {
        final UriBuilder uriBuilder = serviceContext.getServiceUriBuilder();
        final List<Link> links = new ArrayList<>();

        links.add(createLink(HttpMethod.GET,
                             uriBuilder.clone()
                                       .path(MachineService.class, "getMachineById")
                                       .build(machine.getWorkspaceId(), machine.getId())
                                       .toString(),
                             APPLICATION_JSON,
                             "self link"));
        links.add(createLink(HttpMethod.GET,
                             uriBuilder.clone()
                                       .path(MachineService.class, "getMachines")
                                       .build(machine.getWorkspaceId())
                                       .toString(),
                             null,
                             APPLICATION_JSON,
                             Constants.LINK_REL_GET_MACHINES));
        links.add(createLink(HttpMethod.DELETE,
                             uriBuilder.clone()
                                       .path(MachineService.class, "stopMachine")
                                       .build(machine.getWorkspaceId(), machine.getId())
                                       .toString(),
                             Constants.LINK_REL_DESTROY_MACHINE));
        links.add(createLink(HttpMethod.POST,
                             uriBuilder.clone()
                                       .path(MachineService.class, "executeCommandInMachine")
                                       .build(machine.getWorkspaceId(), machine.getId())
                                       .toString(),
                             APPLICATION_JSON,
                             APPLICATION_JSON,
                             Constants.LINK_REL_EXECUTE_COMMAND,
                             newDto(LinkParameter.class).withName("outputChannel")
                                                        .withRequired(false)));
        URI getProcessesUri = uriBuilder.clone()
                                     .path(MachineService.class, "getProcesses")
                                     .build(machine.getWorkspaceId(), machine.getId());
        links.add(createLink(HttpMethod.GET,
                             getProcessesUri.toString(),
                             APPLICATION_JSON,
                             Constants.LINK_REL_GET_PROCESSES));

        injectTerminalLink(machine, serviceContext, links);
        injectExecAgentLink(machine, serviceContext, links);

        // add workspace channel links
        final Link workspaceChannelLink = createLink("GET",
                                                     serviceContext.getBaseUriBuilder()
                                                                   .path("ws")
                                                                   .scheme("https".equals(getProcessesUri.getScheme()) ? "wss" : "ws")
                                                                   .build()
                                                                   .toString(),
                                                     null);
        final LinkParameter channelParameter = newDto(LinkParameter.class).withName("channel")
                                                                          .withRequired(true);

        links.add(cloneDto(workspaceChannelLink).withRel(LINK_REL_ENVIRONMENT_OUTPUT_CHANNEL)
                                                .withParameters(singletonList(cloneDto(channelParameter)
                                                                                      .withDefaultValue(format(ENVIRONMENT_OUTPUT_CHANNEL_TEMPLATE,
                                                                                                               machine.getWorkspaceId())))));

        links.add(cloneDto(workspaceChannelLink).withRel(ENVIRONMENT_STATUS_CHANNEL_TEMPLATE)
                                                .withParameters(singletonList(cloneDto(channelParameter)
                                                                                      .withDefaultValue(format(ENVIRONMENT_STATUS_CHANNEL_TEMPLATE,
                                                                                                               machine.getWorkspaceId())))));

        return machine.withLinks(links);
    }

    protected void injectTerminalLink(MachineDto machine, ServiceContext serviceContext, List<Link> links) {
        final String scheme = serviceContext.getBaseUriBuilder().build().getScheme();
        if (machine.getRuntime() != null) {
            final Collection<ServerDto> servers = machine.getRuntime().getServers().values();
            servers.stream()
                   .filter(server -> TERMINAL_REFERENCE.equals(server.getRef()))
                   .findAny()
                   .ifPresent(terminal -> links.add(createLink("GET",
                                                               UriBuilder.fromUri(terminal.getUrl())
                                                                         .scheme("https".equals(scheme) ? "wss"
                                                                                                        : "ws")
                                                                         .path("/pty")
                                                                         .build()
                                                                         .toString(),
                                                           TERMINAL_REFERENCE)));
        }
    }

    protected void injectExecAgentLink(MachineDto machine, ServiceContext serviceContext, List<Link> links) {
        final String scheme = serviceContext.getBaseUriBuilder().build().getScheme();
        if (machine.getRuntime() != null) {
            final Collection<ServerDto> servers = machine.getRuntime().getServers().values();
            servers.stream()
                   .filter(server -> TERMINAL_REFERENCE.equals(server.getRef()))
                   .findAny()
                   .ifPresent(terminal ->
                                  links.add(createLink("GET",
                                                       UriBuilder.fromUri(terminal.getUrl())
                                                                 .scheme("https".equals(scheme) ? "wss" : "ws")
                                                                 .path("/connect")
                                                                 .build()
                                                                 .toString(),
                                                       EXEC_AGENT_REFERENCE)));
        }
    }

    public MachineProcessDto injectLinks(MachineProcessDto process,
                                         String workspaceId,
                                         String machineId,
                                         ServiceContext serviceContext) {
        final UriBuilder uriBuilder = serviceContext.getServiceUriBuilder();
        final List<Link> links = Lists.newArrayListWithExpectedSize(3);

        links.add(createLink(HttpMethod.DELETE,
                             uriBuilder.clone()
                                       .path(MachineService.class, "stopProcess")
                                       .build(workspaceId,
                                              machineId,
                                              process.getPid())
                                       .toString(),
                             Constants.LINK_REL_STOP_PROCESS));
        links.add(createLink(HttpMethod.GET,
                             uriBuilder.clone()
                                       .path(MachineService.class, "getProcessLogs")
                                       .build(workspaceId,
                                              machineId,
                                              process.getPid())
                                       .toString(),
                             TEXT_PLAIN,
                             Constants.LINK_REL_GET_PROCESS_LOGS));
        links.add(createLink(HttpMethod.GET,
                             uriBuilder.clone()
                                       .path(MachineService.class, "getProcesses")
                                       .build(workspaceId,
                                              machineId)
                                       .toString(),
                             APPLICATION_JSON,
                             Constants.LINK_REL_GET_PROCESSES));

        return process.withLinks(links);
    }
}
