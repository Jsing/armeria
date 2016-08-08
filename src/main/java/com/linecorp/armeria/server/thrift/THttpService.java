/*
 * Copyright 2016 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.server.thrift;

import static com.linecorp.armeria.common.util.Functions.voidFunction;
import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.thrift.AsyncProcessFunction;
import org.apache.thrift.ProcessFunction;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TMemoryBuffer;
import org.apache.thrift.transport.TMemoryInputTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.net.MediaType;

import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.RequestContext.PushHandle;
import com.linecorp.armeria.common.SerializationFormat;
import com.linecorp.armeria.common.http.AggregatedHttpMessage;
import com.linecorp.armeria.common.http.HttpData;
import com.linecorp.armeria.common.http.HttpHeaderNames;
import com.linecorp.armeria.common.http.HttpHeaders;
import com.linecorp.armeria.common.http.HttpRequest;
import com.linecorp.armeria.common.http.HttpResponseWriter;
import com.linecorp.armeria.common.http.HttpStatus;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.common.logging.ResponseLog;
import com.linecorp.armeria.common.thrift.ThriftCall;
import com.linecorp.armeria.common.thrift.ThriftProtocolFactories;
import com.linecorp.armeria.common.thrift.ThriftReply;
import com.linecorp.armeria.common.util.CompletionActions;
import com.linecorp.armeria.internal.thrift.ThriftFunction;
import com.linecorp.armeria.server.Service;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.http.AbstractHttpService;

/**
 * A {@link Service} that handles a Thrift call.
 *
 * @see ThriftProtocolFactories
 */
public class THttpService extends AbstractHttpService {

    private static final Logger logger = LoggerFactory.getLogger(THttpService.class);

    private static final String THRIFT_PROTOCOL_NOT_SUPPORTED = "Specified Thrift protocol not supported";

    private static final String ACCEPT_THRIFT_PROTOCOL_MUST_MATCH_CONTENT_TYPE =
            "Thrift protocol specified in Accept header must match the one specified in Content-Type header";

    private static final Map<SerializationFormat, ThreadLocalTProtocol> FORMAT_TO_THREAD_LOCAL_INPUT_PROTOCOL =
            createFormatToThreadLocalTProtocolMap();

    /**
     * Creates a new instance with the specified service implementation, supporting all thrift protocols and
     * defaulting to {@link SerializationFormat#THRIFT_BINARY TBinary} protocol when the client doesn't specify
     * one.
     *
     * Currently, the only way to specify a serialization format is by using the HTTP session
     * protocol and setting the Content-Type header to the appropriate {@link SerializationFormat#mediaType()}.
     *
     * @param implementation an implementation of {@code *.Iface} or {@code *.AsyncIface} service interface
     *                       generated by the Apache Thrift compiler
     */
    public static THttpService of(Object implementation) {
        return of(implementation, SerializationFormat.THRIFT_BINARY);
    }

    /**
     * Creates a new instance with the specified service implementation, supporting all thrift protocols and
     * defaulting to the specified {@code defaultSerializationFormat} when the client doesn't specify one.
     *
     * Currently, the only way to specify a serialization format is by using the HTTP session
     * protocol and setting the Content-Type header to the appropriate {@link SerializationFormat#mediaType()}.
     *
     *
     * @param implementation an implementation of {@code *.Iface} or {@code *.AsyncIface} service interface
     *                       generated by the Apache Thrift compiler
     * @param defaultSerializationFormat the default serialization format to use when not specified by the
     *                                   client
     */
    public static THttpService of(Object implementation,
                                  SerializationFormat defaultSerializationFormat) {

        return new THttpService(ThriftCallService.of(implementation),
                                defaultSerializationFormat, SerializationFormat.ofThrift());
    }

    /**
     * Creates a new instance with the specified service implementation, supporting only the formats specified
     * and defaulting to the specified {@code defaultSerializationFormat} when the client doesn't specify one.
     *
     * Currently, the only way to specify a serialization format is by using the HTTP session protocol and
     * setting the Content-Type header to the appropriate {@link SerializationFormat#mediaType()}.
     *
     * @param implementation an implementation of {@code *.Iface} or {@code *.AsyncIface} service interface
     *                       generated by the Apache Thrift compiler
     * @param defaultSerializationFormat the default serialization format to use when not specified by the
     *                                   client
     * @param otherAllowedSerializationFormats other serialization formats that should be supported by this
     *                                         service in addition to the default
     */
    public static THttpService ofFormats(
            Object implementation,
            SerializationFormat defaultSerializationFormat,
            SerializationFormat... otherAllowedSerializationFormats) {

        requireNonNull(otherAllowedSerializationFormats, "otherAllowedSerializationFormats");
        return ofFormats(implementation,
                         defaultSerializationFormat,
                         Arrays.asList(otherAllowedSerializationFormats));
    }

    /**
     * Creates a new instance with the specified service implementation, supporting the protocols specified
     * in {@code allowedSerializationFormats} and defaulting to the specified {@code defaultSerializationFormat}
     * when the client doesn't specify one.
     *
     * Currently, the only way to specify a serialization format is by using the HTTP session protocol and
     * setting the Content-Type header to the appropriate {@link SerializationFormat#mediaType()}.
     *
     * @param implementation an implementation of {@code *.Iface} or {@code *.AsyncIface} service interface
     *                       generated by the Apache Thrift compiler
     * @param defaultSerializationFormat the default serialization format to use when not specified by the
     *                                   client
     * @param otherAllowedSerializationFormats other serialization formats that should be supported by this
     *                                         service in addition to the default
     */
    public static THttpService ofFormats(
            Object implementation,
            SerializationFormat defaultSerializationFormat,
            Iterable<SerializationFormat> otherAllowedSerializationFormats) {

        requireNonNull(otherAllowedSerializationFormats, "otherAllowedSerializationFormats");
        EnumSet<SerializationFormat> allowedSerializationFormatsSet = EnumSet.of(defaultSerializationFormat);
        otherAllowedSerializationFormats.forEach(allowedSerializationFormatsSet::add);

        return new THttpService(ThriftCallService.of(implementation),
                                defaultSerializationFormat, allowedSerializationFormatsSet);
    }

    /**
     * Creates a new decorator that supports all thrift protocols and defaults to
     * {@link SerializationFormat#THRIFT_BINARY TBinary} protocol when the client doesn't specify one.
     * Currently, the only way to specify a serialization format is by using the HTTP session
     * protocol and setting the Content-Type header to the appropriate {@link SerializationFormat#mediaType()}.
     */
    public static Function<Service<ThriftCall, ThriftReply>, THttpService> newDecorator() {
        return newDecorator(SerializationFormat.THRIFT_BINARY);
    }

    /**
     * Creates a new decorator that supports all thrift protocols and defaults to the specified
     * {@code defaultSerializationFormat} when the client doesn't specify one.
     * Currently, the only way to specify a serialization format is by using the HTTP session
     * protocol and setting the Content-Type header to the appropriate {@link SerializationFormat#mediaType()}.
     *
     * @param defaultSerializationFormat the default serialization format to use when not specified by the
     *                                   client
     */
    public static Function<Service<ThriftCall, ThriftReply>, THttpService> newDecorator(
            SerializationFormat defaultSerializationFormat) {

        return delegate -> new THttpService(delegate,
                                            defaultSerializationFormat, SerializationFormat.ofThrift());
    }

    /**
     * Creates a new decorator that supports only the formats specified and defaults to the specified
     * {@code defaultSerializationFormat} when the client doesn't specify one.
     * Currently, the only way to specify a serialization format is by using the HTTP session protocol and
     * setting the Content-Type header to the appropriate {@link SerializationFormat#mediaType()}.
     *
     * @param defaultSerializationFormat the default serialization format to use when not specified by the
     *                                   client
     * @param otherAllowedSerializationFormats other serialization formats that should be supported by this
     *                                         service in addition to the default
     */
    public static Function<Service<ThriftCall, ThriftReply>, THttpService> newDecorator(
            SerializationFormat defaultSerializationFormat,
            SerializationFormat... otherAllowedSerializationFormats) {

        requireNonNull(otherAllowedSerializationFormats, "otherAllowedSerializationFormats");
        return newDecorator(defaultSerializationFormat, Arrays.asList(otherAllowedSerializationFormats));
    }

    /**
     * Creates a new decorator that supports the protocols specified in {@code allowedSerializationFormats} and
     * defaults to the specified {@code defaultSerializationFormat} when the client doesn't specify one.
     * Currently, the only way to specify a serialization format is by using the HTTP session protocol and
     * setting the Content-Type header to the appropriate {@link SerializationFormat#mediaType()}.
     *
     * @param defaultSerializationFormat the default serialization format to use when not specified by the
     *                                   client
     * @param otherAllowedSerializationFormats other serialization formats that should be supported by this
     *                                         service in addition to the default
     */
    public static Function<Service<ThriftCall, ThriftReply>, THttpService> newDecorator(
            SerializationFormat defaultSerializationFormat,
            Iterable<SerializationFormat> otherAllowedSerializationFormats) {

        requireNonNull(otherAllowedSerializationFormats, "otherAllowedSerializationFormats");
        EnumSet<SerializationFormat> allowedSerializationFormatsSet = EnumSet.of(defaultSerializationFormat);
        otherAllowedSerializationFormats.forEach(allowedSerializationFormatsSet::add);

        return delegate -> new THttpService(delegate,
                                            defaultSerializationFormat, allowedSerializationFormatsSet);
    }

    private final Service<ThriftCall, ThriftReply> delegate;
    private final SerializationFormat defaultSerializationFormat;
    private final Set<SerializationFormat> allowedSerializationFormats;
    private final ThriftCallService thriftService;

    // TODO(trustin): Make this contructor private once we remove ThriftService.
    THttpService(Service<ThriftCall, ThriftReply> delegate,
                 SerializationFormat defaultSerializationFormat,
                 Set<SerializationFormat> allowedSerializationFormats) {

        requireNonNull(delegate, "delegate");
        requireNonNull(defaultSerializationFormat, "defaultSerializationFormat");
        requireNonNull(allowedSerializationFormats, "allowedSerializationFormats");

        this.delegate = delegate;
        thriftService = findThriftService(delegate);

        this.defaultSerializationFormat = defaultSerializationFormat;
        this.allowedSerializationFormats = Sets.immutableEnumSet(allowedSerializationFormats);
    }

    private static ThriftCallService findThriftService(Service<? ,?> delegate) {
        return delegate.as(ThriftCallService.class).orElseThrow(
                    () -> new IllegalStateException("service being decorated is not a ThriftService: " +
                                                    delegate));
    }

    /**
     * Returns the Thrift service object that implements {@code *.Iface} or {@code *.AsyncIface}.
     */
    public Object implementation() {
        return thriftService.implementation();
    }

    /**
     * Returns the Thrift service interfaces ({@code *.Iface} or {@code *.AsyncIface}) the Thrift service
     * object implements.
     */
    public Set<Class<?>> interfaces() {
        return thriftService.metadata().interfaces();
    }

    /**
     * Returns the allowed serialization formats of this service.
     */
    public Set<SerializationFormat> allowedSerializationFormats() {
        return allowedSerializationFormats;
    }

    /**
     * Returns the default serialization format of this service.
     */
    public SerializationFormat defaultSerializationFormat() {
        return defaultSerializationFormat;
    }

    @Override
    protected void doPost(ServiceRequestContext ctx, HttpRequest req, HttpResponseWriter res) {

        final SerializationFormat serializationFormat =
                validateRequestAndDetermineSerializationFormat(req, res);

        if (serializationFormat == null) {
            return;
        }

        ctx.requestLogBuilder().serializationFormat(serializationFormat);
        req.aggregate().handle(voidFunction((aReq, cause) -> {
            if (cause != null) {
                res.respond(HttpStatus.INTERNAL_SERVER_ERROR,
                            MediaType.PLAIN_TEXT_UTF_8, Throwables.getStackTraceAsString(cause));
                return;
            }

            decodeAndInvoke(ctx, aReq, serializationFormat, res);
        })).exceptionally(CompletionActions::log);
    }

    private SerializationFormat validateRequestAndDetermineSerializationFormat(
            HttpRequest req, HttpResponseWriter res) {

        final HttpHeaders headers = req.headers();
        final SerializationFormat serializationFormat;
        final CharSequence contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType != null) {
            serializationFormat = SerializationFormat.fromMediaType(contentType.toString())
                                                     .orElse(defaultSerializationFormat);
            if (!allowedSerializationFormats.contains(serializationFormat)) {
                res.respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                            MediaType.PLAIN_TEXT_UTF_8, THRIFT_PROTOCOL_NOT_SUPPORTED);
                return null;
            }
        } else {
            serializationFormat = defaultSerializationFormat;
        }

        final CharSequence accept = headers.get(HttpHeaderNames.ACCEPT);
        if (accept != null) {
            // If accept header is present, make sure it is sane. Currently, we do not support accept
            // headers with a different format than the content type header.
            SerializationFormat outputSerializationFormat =
                    SerializationFormat.fromMediaType(accept.toString()).orElse(serializationFormat);
            if (outputSerializationFormat != serializationFormat) {
                res.respond(HttpStatus.NOT_ACCEPTABLE,
                            MediaType.PLAIN_TEXT_UTF_8, ACCEPT_THRIFT_PROTOCOL_MUST_MATCH_CONTENT_TYPE);
                return null;
            }
        }

        return serializationFormat;
    }

    private void decodeAndInvoke(
            ServiceRequestContext ctx, AggregatedHttpMessage req,
            SerializationFormat serializationFormat, HttpResponseWriter res) {

        final TProtocol inProto = FORMAT_TO_THREAD_LOCAL_INPUT_PROTOCOL.get(serializationFormat).get();
        inProto.reset();
        final TMemoryInputTransport inTransport = (TMemoryInputTransport) inProto.getTransport();
        final HttpData content = req.content();
        inTransport.reset(content.array(), content.offset(), content.length());

        final int seqId;
        final ThriftFunction f;
        final TBase<TBase<?, ?>, TFieldIdEnum> args;
        try {
            final TMessage header;
            try {
                header = inProto.readMessageBegin();
            } catch (Exception e) {
                logger.debug("{} Failed to decode Thrift header:", ctx, e);
                res.respond(HttpStatus.BAD_REQUEST,
                            MediaType.PLAIN_TEXT_UTF_8,
                            "Failed to decode Thrift header: " + Throwables.getStackTraceAsString(e));
                return;
            }

            seqId = header.seqid;

            final byte typeValue = header.type;
            final String methodName = header.name;

            // Basic sanity check. We usually should never fail here.
            if (typeValue != TMessageType.CALL && typeValue != TMessageType.ONEWAY) {
                final TApplicationException cause = new TApplicationException(
                        TApplicationException.INVALID_MESSAGE_TYPE,
                        "unexpected TMessageType: " + typeString(typeValue));

                respond(serializationFormat, seqId, methodName, cause, res);
                return;
            }

            // Ensure that such a method exists.
            f = thriftService.metadata().function(methodName);
            if (f == null) {
                final TApplicationException cause = new TApplicationException(
                        TApplicationException.UNKNOWN_METHOD, "unknown method: " + methodName);

                respond(serializationFormat, seqId, methodName, cause, res);
                return;
            }

            // Decode the invocation parameters.
            try {
                if (f.isAsync()) {
                    AsyncProcessFunction<Object, TBase<TBase<?, ?>, TFieldIdEnum>, Object> asyncFunc =
                            f.asyncFunc();

                    args = asyncFunc.getEmptyArgsInstance();
                    args.read(inProto);
                    inProto.readMessageEnd();
                } else {
                    ProcessFunction<Object, TBase<TBase<?, ?>, TFieldIdEnum>> syncFunc = f.syncFunc();

                    args = syncFunc.getEmptyArgsInstance();
                    args.read(inProto);
                    inProto.readMessageEnd();
                }
            } catch (Exception e) {
                // Failed to decode the invocation parameters.
                logger.debug("{} Failed to decode Thrift arguments:", ctx, e);

                final TApplicationException cause = new TApplicationException(
                        TApplicationException.PROTOCOL_ERROR, "failed to decode arguments: " + e);

                respond(serializationFormat, seqId, methodName, cause, res);
                return;
            }
        } finally {
            inTransport.clear();
        }

        invoke(ctx, serializationFormat, seqId, f, args, res);
    }

    private static String typeString(byte typeValue) {
        switch (typeValue) {
            case TMessageType.CALL:
                return "CALL";
            case TMessageType.REPLY:
                return "REPLY";
            case TMessageType.EXCEPTION:
                return "EXCEPTION";
            case TMessageType.ONEWAY:
                return "ONEWAY";
            default:
                return "UNKNOWN(" + (typeValue & 0xFF) + ')';
        }
    }

    private void invoke(
            ServiceRequestContext ctx, SerializationFormat serializationFormat, int seqId,
            ThriftFunction func, TBase<TBase<?, ?>, TFieldIdEnum> args, HttpResponseWriter res) {

        final ThriftCall call = new ThriftCall(seqId, func.serviceType(), func.name(), args);
        final ThriftReply reply;
        ctx.requestLogBuilder().attr(RequestLog.RPC_REQUEST).set(call);

        try (PushHandle ignored = RequestContext.push(ctx)) {
            reply = delegate.serve(ctx, call);
            ctx.responseLogBuilder().attr(ResponseLog.RPC_RESPONSE).set(reply);
        } catch (Throwable cause) {
            handleException(serializationFormat, seqId, func, cause, res);
            return;
        }

        reply.handle(voidFunction((result, cause) -> {
            if (cause != null) {
                handleException(serializationFormat, seqId, func, cause, res);
                return;
            }

            if (func.isOneway()) {
                respond(serializationFormat, HttpData.EMPTY_DATA, res);
                return;
            }

            try {
                TBase<TBase<?, ?>, TFieldIdEnum> wrappedResult = func.newResult();
                func.setSuccess(wrappedResult, result);
                respond(serializationFormat, seqId, func, wrappedResult, res);
            } catch (Throwable t) {
                respond(serializationFormat, seqId, func, t, res);
            }
        })).exceptionally(CompletionActions::log);
    }

    private static void handleException(
            SerializationFormat serializationFormat,
            int seqId, ThriftFunction func, Throwable cause, HttpResponseWriter res) {

        final TBase<TBase<?, ?>, TFieldIdEnum> result = func.newResult();
        if (func.setException(result, cause)) {
            respond(serializationFormat, seqId, func, result, res);
        } else {
            respond(serializationFormat, seqId, func, cause, res);
        }
    }

    private static void respond(SerializationFormat serializationFormat, int seqId,
                                ThriftFunction func, TBase<TBase<?, ?>, TFieldIdEnum> result,
                                HttpResponseWriter res) {
        respond(serializationFormat,
                encodeSuccess(serializationFormat, func, seqId, result),
                res);
    }

    private static void respond(SerializationFormat serializationFormat, int seqId,
                                ThriftFunction func, Throwable cause, HttpResponseWriter res) {

        final TBase<TBase<?, ?>, TFieldIdEnum> result = func.newResult();
        if (func.setException(result, cause)) {
            respond(serializationFormat, seqId, func, result, res);
        } else {
            respond(serializationFormat, seqId, func.name(), cause, res);
        }
    }

    private static void respond(SerializationFormat serializationFormat, int seqId,
                                String methodName, Throwable cause, HttpResponseWriter res) {

        final HttpData content = encodeException(serializationFormat, seqId, methodName, cause);
        respond(serializationFormat, content, res);
    }

    private static void respond(SerializationFormat serializationFormat,
                                HttpData content, HttpResponseWriter res) {

        res.respond(HttpStatus.OK, serializationFormat.mediaType(), content);
    }

    private static HttpData encodeSuccess(SerializationFormat serializationFormat,
                                          ThriftFunction func, int seqId,
                                          TBase<TBase<?, ?>, TFieldIdEnum> result) {

        final TMemoryBuffer buf = new TMemoryBuffer(128);
        final TProtocol outProto = ThriftProtocolFactories.get(serializationFormat).getProtocol(buf);

        try {
            outProto.writeMessageBegin(new TMessage(func.name(), TMessageType.REPLY, seqId));
            result.write(outProto);
            outProto.writeMessageEnd();
        } catch (TException e) {
            throw new Error(e); // Should never reach here.
        }

        return HttpData.of(buf.getArray(), 0, buf.length());
    }

    private static HttpData encodeException(SerializationFormat serializationFormat,
                                            int seqId, String methodName, Throwable cause) {

        final TApplicationException appException;
        if (cause instanceof TApplicationException) {
            appException = (TApplicationException) cause;
        } else {
            appException = new TApplicationException(TApplicationException.INTERNAL_ERROR,
                                                     cause.toString());
        }

        final TMemoryBuffer buf = new TMemoryBuffer(128);
        final TProtocol outProto = ThriftProtocolFactories.get(serializationFormat).getProtocol(buf);

        try {
            outProto.writeMessageBegin(new TMessage(methodName, TMessageType.EXCEPTION, seqId));
            appException.write(outProto);
            outProto.writeMessageEnd();
        } catch (TException e) {
            throw new Error(e); // Should never reach here.
        }

        return HttpData.of(buf.getArray(), 0, buf.length());
    }

    private static Map<SerializationFormat, ThreadLocalTProtocol> createFormatToThreadLocalTProtocolMap() {
        return Maps.immutableEnumMap(
                SerializationFormat.ofThrift().stream().collect(
                        Collectors.toMap(Function.identity(),
                                         f -> new ThreadLocalTProtocol(ThriftProtocolFactories.get(f)))));
    }

    private static final class ThreadLocalTProtocol extends ThreadLocal<TProtocol> {

        private final TProtocolFactory protoFactory;

        private ThreadLocalTProtocol(TProtocolFactory protoFactory) {
            this.protoFactory = protoFactory;
        }

        @Override
        protected TProtocol initialValue() {
            return protoFactory.getProtocol(new TMemoryInputTransport());
        }
    }
}
