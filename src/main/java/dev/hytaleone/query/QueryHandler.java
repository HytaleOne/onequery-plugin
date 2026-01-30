package dev.hytaleone.query;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.hytaleone.query.auth.ChallengeTokenGenerator;
import dev.hytaleone.query.auth.TokenValidator;
import dev.hytaleone.query.network.NetworkModule;
import dev.hytaleone.query.network.model.NetworkSnapshot;
import dev.hytaleone.query.protocol.v1.V1Protocol;
import dev.hytaleone.query.protocol.v1.V1RequestParser;
import dev.hytaleone.query.protocol.v1.V1ResponseBuilder;
import dev.hytaleone.query.protocol.v2.V2Protocol;
import dev.hytaleone.query.protocol.v2.V2RequestParser;
import dev.hytaleone.query.protocol.v2.V2ResponseBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.logging.Level;

public class QueryHandler extends ChannelInboundHandlerAdapter {

    @Nonnull
    private final HytaleLogger logger;

    @Nonnull
    private final ChallengeTokenGenerator challengeTokenGenerator;

    @Nullable
    private final TokenValidator tokenValidator;

    @Nullable
    private final NetworkModule networkModule;

    private final boolean legacyEnabled;

    public QueryHandler(@Nonnull HytaleLogger logger,
                        @Nonnull ChallengeTokenGenerator challengeTokenGenerator,
                        @Nullable TokenValidator tokenValidator,
                        boolean legacyEnabled) {
        this(logger, challengeTokenGenerator, tokenValidator, null, legacyEnabled);
    }

    public QueryHandler(@Nonnull HytaleLogger logger,
                        @Nonnull ChallengeTokenGenerator challengeTokenGenerator,
                        @Nullable TokenValidator tokenValidator,
                        @Nullable NetworkModule networkModule,
                        boolean legacyEnabled) {
        this.logger = logger;
        this.challengeTokenGenerator = challengeTokenGenerator;
        this.tokenValidator = tokenValidator;
        this.networkModule = networkModule;
        this.legacyEnabled = legacyEnabled;
    }

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    public void channelRead(@Nonnull ChannelHandlerContext ctx, @Nonnull Object msg) throws Exception {
        if (msg instanceof DatagramPacket packet) {
            ByteBuf content = packet.content();

            if (V2RequestParser.isRequest(content)) {
                handleV2Query(ctx, packet);
                return;
            }

            if (V1RequestParser.isRequest(content)) {
                if (legacyEnabled) {
                    handleV1Query(ctx, packet);
                } else {
                    packet.release();
                }
                return;
            }
        }

        ctx.fireChannelRead(msg);
    }

    private void handleV2Query(@Nonnull ChannelHandlerContext ctx, @Nonnull DatagramPacket request) {
        try {
            InetSocketAddress sender = request.sender();
            ByteBuf content = request.content();
            V2Protocol.QueryType queryType = V2Protocol.QueryType.fromCode(V2RequestParser.getQueryType(content));

            logger.at(Level.FINE).log("V2 query request (type=%s) from %s", queryType, sender);

            if (queryType == V2Protocol.QueryType.CHALLENGE) {
                handleChallengeRequest(ctx, request);
                return;
            }

            byte[] challengeToken = V2RequestParser.extractChallengeToken(content);
            if (!challengeTokenGenerator.validateToken(challengeToken, sender.getAddress())) {
                logger.at(Level.FINE).log("Invalid challenge token from %s, dropping request", sender);
                return;
            }

            if (queryType == null) {
                queryType = V2Protocol.QueryType.BASIC;
            }

            byte[] authToken = V2RequestParser.extractAuthToken(content);

            String endpoint = queryType.endpoint();
            if (endpoint != null && tokenValidator != null && !tokenValidator.isAccessAllowed(endpoint, authToken)) {
                logger.at(Level.FINE).log("Access denied for endpoint '%s' from %s", endpoint, sender);
                sendAuthRequiredResponse(ctx, request);
                return;
            }

            int requestId = V2RequestParser.getRequestId(content);
            int offset = V2RequestParser.getOffset(content);

            ByteBuf response;

            if (isNetworkMode()) {
                response = buildNetworkResponse(ctx, queryType, requestId, offset);
            } else {
                response = queryType == V2Protocol.QueryType.PLAYERS
                        ? V2ResponseBuilder.buildPlayersResponse(ctx.alloc(), requestId, offset)
                        : V2ResponseBuilder.buildBasicResponse(ctx.alloc(), requestId, (short) 0);
            }

            ctx.writeAndFlush(new DatagramPacket(response, sender));

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to process v2 query from %s",
                    request.sender());
        } finally {
            request.release();
        }
    }

    private void handleChallengeRequest(@Nonnull ChannelHandlerContext ctx, @Nonnull DatagramPacket request) {
        InetSocketAddress sender = request.sender();
        byte[] token = challengeTokenGenerator.generateToken(sender.getAddress());
        ByteBuf response = V2ResponseBuilder.buildChallengeResponse(ctx.alloc(), token);
        ctx.writeAndFlush(new DatagramPacket(response, sender));
        logger.at(Level.FINE).log("Sent challenge token to %s", sender);
    }

    private void sendAuthRequiredResponse(@Nonnull ChannelHandlerContext ctx, @Nonnull DatagramPacket request) {
        int requestId = V2RequestParser.getRequestId(request.content());

        ByteBuf response = V2ResponseBuilder.buildAuthRequiredResponse(ctx.alloc(), requestId);
        ctx.writeAndFlush(new DatagramPacket(response, request.sender()));
    }

    private void handleV1Query(@Nonnull ChannelHandlerContext ctx, @Nonnull DatagramPacket request) {
        try {
            byte queryType = V1RequestParser.getQueryType(request.content());

            logger.at(Level.FINE).log("V1 query request (type=%d) from %s", queryType, request.sender());

            ByteBuf response;
            if (isNetworkMode()) {
                NetworkSnapshot snapshot = networkModule.getNetworkSnapshotSync();
                response = queryType == V1Protocol.TYPE_FULL
                        ? V1ResponseBuilder.buildFullResponse(ctx.alloc(), snapshot)
                        : V1ResponseBuilder.buildBasicResponse(ctx.alloc(), snapshot);
            } else {
                response = queryType == V1Protocol.TYPE_FULL
                        ? V1ResponseBuilder.buildFullResponse(ctx.alloc())
                        : V1ResponseBuilder.buildBasicResponse(ctx.alloc());
            }

            ctx.writeAndFlush(new DatagramPacket(response, request.sender()));

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to process v1 query from %s",
                    request.sender());
        } finally {
            request.release();
        }
    }

    @Override
    public void exceptionCaught(@Nonnull ChannelHandlerContext ctx, @Nonnull Throwable cause) {
        logger.at(Level.WARNING).withCause(cause).log("Exception in query handler");
        ctx.fireExceptionCaught(cause);
    }

    private boolean isNetworkMode() {
        return networkModule != null && networkModule.isEnabled() && networkModule.shouldAggregate();
    }

    @Nonnull
    private ByteBuf buildNetworkResponse(@Nonnull ChannelHandlerContext ctx,
                                         @Nonnull V2Protocol.QueryType queryType,
                                         int requestId,
                                         int offset) {
        NetworkSnapshot snapshot = networkModule.getNetworkSnapshotSync();

        return queryType == V2Protocol.QueryType.PLAYERS
                ? V2ResponseBuilder.buildPlayersResponse(ctx.alloc(), requestId, snapshot, offset)
                : V2ResponseBuilder.buildBasicResponse(ctx.alloc(), requestId, snapshot);
    }
}
