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
package org.eclipse.che.ide.api.machine.execagent;

import com.google.inject.Inject;

import org.eclipse.che.api.core.model.machine.Command;
import org.eclipse.che.api.machine.shared.dto.execagent.GetProcessLogsRequestDto;
import org.eclipse.che.api.machine.shared.dto.execagent.GetProcessLogsResponseDto;
import org.eclipse.che.api.machine.shared.dto.execagent.GetProcessRequestDto;
import org.eclipse.che.api.machine.shared.dto.execagent.GetProcessResponseDto;
import org.eclipse.che.api.machine.shared.dto.execagent.GetProcessesRequestDto;
import org.eclipse.che.api.machine.shared.dto.execagent.GetProcessesResponseDto;
import org.eclipse.che.api.machine.shared.dto.execagent.ProcessKillRequestDto;
import org.eclipse.che.api.machine.shared.dto.execagent.ProcessKillResponseDto;
import org.eclipse.che.api.machine.shared.dto.execagent.ProcessStartRequestDto;
import org.eclipse.che.api.machine.shared.dto.execagent.ProcessStartResponseDto;
import org.eclipse.che.api.machine.shared.dto.execagent.ProcessSubscribeRequestDto;
import org.eclipse.che.api.machine.shared.dto.execagent.ProcessSubscribeResponseDto;
import org.eclipse.che.api.machine.shared.dto.execagent.ProcessUnSubscribeRequestDto;
import org.eclipse.che.api.machine.shared.dto.execagent.ProcessUnSubscribeResponseDto;
import org.eclipse.che.api.machine.shared.dto.execagent.UpdateSubscriptionRequestDto;
import org.eclipse.che.api.machine.shared.dto.execagent.UpdateSubscriptionResponseDto;
import org.eclipse.che.api.machine.shared.dto.execagent.event.DtoWithPidDto;
import org.eclipse.che.api.machine.shared.dto.execagent.event.ProcessDiedEventDto;
import org.eclipse.che.api.machine.shared.dto.execagent.event.ProcessStartedEventDto;
import org.eclipse.che.api.machine.shared.dto.execagent.event.ProcessStdErrEventDto;
import org.eclipse.che.api.machine.shared.dto.execagent.event.ProcessStdOutEventDto;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.ide.api.machine.ExecAgentCommandManager;
import org.eclipse.che.ide.api.machine.ExecAgentEventManager;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.jsonrpc.RequestTransmitter;
import org.eclipse.che.ide.util.loging.Log;

import javax.inject.Singleton;
import java.util.List;

import static org.eclipse.che.ide.util.StringUtils.join;

/**
 * @author Dmitry Kuleshov
 */
@Singleton
public class JsonRpcExecAgentCommandManager implements ExecAgentCommandManager {
    private final DtoFactory            dtoFactory;
    private final RequestTransmitter    transmitter;
    private final ExecAgentEventManager eventManager;

    @Inject
    protected JsonRpcExecAgentCommandManager(DtoFactory dtoFactory, RequestTransmitter transmitter, ExecAgentEventManager eventManager) {
        this.dtoFactory = dtoFactory;
        this.transmitter = transmitter;
        this.eventManager = eventManager;
    }

    @Override
    public ExecAgentPromise<ProcessStartResponseDto> startProcess(Command command) {
        final String name = command.getName();
        final String commandLine = command.getCommandLine();
        final String type = command.getType();

        Log.debug(getClass(), "Starting a process. Name: " + name + ", command line: " + commandLine + ", type: " + type);

        final ProcessStartRequestDto dto = dtoFactory.createDto(ProcessStartRequestDto.class)
                                                     .withCommandLine(commandLine)
                                                     .withName(name)
                                                     .withType(type);

        final ExecAgentPromise<ProcessStartResponseDto> execAgentPromise = new ExecAgentPromise<>();

        transmitter.transmitRequest("exec-agent", "process.start", dto, ProcessStartResponseDto.class).then(
                new Operation<ProcessStartResponseDto>() {
                    @Override
                    public void apply(ProcessStartResponseDto arg) throws OperationException {
                        subscribe(execAgentPromise, arg);
                    }
                });

        return execAgentPromise;
    }

    @Override
    public Promise<ProcessKillResponseDto> killProcess(final int pid) {
        Log.debug(getClass(), "Killing a process. PID: " + pid);

        final ProcessKillRequestDto dto = dtoFactory.createDto(ProcessKillRequestDto.class)
                                                    .withPid(pid);

        return transmitter.transmitRequest("exec-agent", "process.kill", dto, ProcessKillResponseDto.class).then(
                new Operation<ProcessKillResponseDto>() {
                    @Override
                    public void apply(ProcessKillResponseDto arg) throws OperationException {
                        eventManager.cleanPidOperations(pid);
                    }
                });
    }

    @Override
    public ExecAgentPromise<ProcessSubscribeResponseDto> subscribe(int pid, List<String> eventTypes, String after) {
        Log.debug(getClass(), "Subscribing to a process. PID: " + pid + ", event types: " + eventTypes + ", after timestamp: " + after);

        final ProcessSubscribeRequestDto dto = dtoFactory.createDto(ProcessSubscribeRequestDto.class)
                                                         .withPid(pid)
                                                         .withEventTypes(join(eventTypes, ","))
                                                         .withAfter(after);

        final ExecAgentPromise<ProcessSubscribeResponseDto> execAgentPromise = new ExecAgentPromise<>();

        transmitter.transmitRequest("exec-agent", "process.subscribe", dto, ProcessSubscribeResponseDto.class).then(
                new Operation<ProcessSubscribeResponseDto>() {
                    @Override
                    public void apply(ProcessSubscribeResponseDto arg) throws OperationException {
                        subscribe(execAgentPromise, arg);
                    }
                });

        return execAgentPromise;
    }

    @Override
    public Promise<ProcessUnSubscribeResponseDto> unsubscribe(int pid, List<String> eventTypes, String after) {
        Log.debug(getClass(), "Unsubscribing to a process. PID: " + pid + ", event types: " + eventTypes + ", after timestamp: " + after);

        final ProcessUnSubscribeRequestDto dto = dtoFactory.createDto(ProcessUnSubscribeRequestDto.class)
                                                           .withPid(pid)
                                                           .withEventTypes(join(eventTypes, ","))
                                                           .withAfter(after);

        return transmitter.transmitRequest("exec-agent", "process.unsubscribe", dto, ProcessUnSubscribeResponseDto.class);
    }

    @Override
    public Promise<UpdateSubscriptionResponseDto> updateSubscription(int pid, List<String> eventTypes) {
        Log.debug(getClass(), "Updating subscription to a process. PID: " + pid + ", event types: " + eventTypes);

        final UpdateSubscriptionRequestDto dto = dtoFactory.createDto(UpdateSubscriptionRequestDto.class)
                                                           .withPid(pid)
                                                           .withEventTypes(join(eventTypes, ","));

        return transmitter.transmitRequest("exec-agent", "process.updateSubscriber", dto, UpdateSubscriptionResponseDto.class);
    }

    @Override
    public Promise<List<GetProcessLogsResponseDto>> getProcessLogs(int pid, String from, String till, int limit, int skip) {
        Log.debug(getClass(),
                  "Getting process logs" +
                  ". PID: " + pid +
                  ", from: " + from +
                  ", till: " + till +
                  ", limit: " + limit +
                  ", skip: " + skip);


        final GetProcessLogsRequestDto dto = dtoFactory.createDto(GetProcessLogsRequestDto.class)
                                                       .withPid(pid)
                                                       .withFrom(from)
                                                       .withTill(till)
                                                       .withLimit(limit)
                                                       .withSkip(skip);

        return transmitter.transmitRequestForList("exec-agent", "process.getLogs", dto, GetProcessLogsResponseDto.class);
    }

    @Override
    public Promise<GetProcessResponseDto> getProcess(int pid) {
        Log.debug(getClass(), "Getting process info. PID: " + pid);

        final GetProcessRequestDto dto = dtoFactory.createDto(GetProcessRequestDto.class)
                                                   .withPid(pid);

        return transmitter.transmitRequest("exec-agent", "process.getProcess", dto, GetProcessResponseDto.class);
    }

    @Override
    public Promise<List<GetProcessesResponseDto>> getProcesses(boolean all) {
        Log.debug(getClass(), "Getting processes info. All: " + all);

        final GetProcessesRequestDto dto = dtoFactory.createDto(GetProcessesRequestDto.class)
                                                     .withAll(all);

        return transmitter.transmitRequestForList("exec-agent", "process.getProcesses", dto, GetProcessesResponseDto.class);
    }

    private <T extends DtoWithPidDto> void subscribe(ExecAgentPromise<T> promise, T arg) throws OperationException {
        final Integer pid = arg.getPid();

        if (promise.hasProcessDiedEventOperation()) {
            final Operation<ProcessDiedEventDto> operation = promise.getProcessDiedEventDtoOperation();
            eventManager.registerProcessDiedOperation(pid, operation);
        }

        if (promise.hasProcessStartedEventOperation()) {
            final Operation<ProcessStartedEventDto> operation = promise.getProcessStartedEventDtoOperation();
            eventManager.registerProcessStartedOperation(pid, operation);
        }

        if (promise.hasProcessStdOutEventOperation()) {
            final Operation<ProcessStdOutEventDto> operation = promise.getProcessStdOutEventDtoOperation();
            eventManager.registerProcessStdOutOperation(pid, operation);
        }

        if (promise.hasProcessStdErrEventOperation()) {
            final Operation<ProcessStdErrEventDto> operation = promise.getProcessStdErrEventDtoOperation();
            eventManager.registerProcessStdErrOperation(pid, operation);
        }

        if (promise.hasOperation()) {
            promise.getOperation().apply(arg);
        }
    }
}
