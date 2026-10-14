package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.server.FxTradingService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/// Channel registration. Client bound payloads only get their types here, the handlers live in the client
/// entry point so no client class is ever loaded on a dedicated server.
public final class FxNetwork {

    private static final String VERSION = "7";

    private FxNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(FxNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(FxSubscribePayload.TYPE, FxSubscribePayload.STREAM_CODEC, FxTradingService::onSubscribe);
        registrar.playToServer(FxUnsubscribePayload.TYPE, FxUnsubscribePayload.STREAM_CODEC, FxTradingService::onUnsubscribe);
        registrar.playToServer(FxOrderPayload.TYPE, FxOrderPayload.STREAM_CODEC, FxTradingService::onOrder);
        registrar.playToServer(FxClosePositionPayload.TYPE, FxClosePositionPayload.STREAM_CODEC, FxTradingService::onClosePosition);
        registrar.playToServer(FxCloseAllPayload.TYPE, FxCloseAllPayload.STREAM_CODEC, FxTradingService::onCloseAll);
        registrar.playToServer(FxDepositPayload.TYPE, FxDepositPayload.STREAM_CODEC, FxTradingService::onDeposit);
        registrar.playToServer(FxWithdrawPayload.TYPE, FxWithdrawPayload.STREAM_CODEC, FxTradingService::onWithdraw);
        registrar.playToServer(FxTutorialSeenPayload.TYPE, FxTutorialSeenPayload.STREAM_CODEC, FxTradingService::onTutorialSeen);
        registrar.playToServer(FxAccountOpenPayload.TYPE, FxAccountOpenPayload.STREAM_CODEC, FxTradingService::onAccountOpen);
        registrar.playToServer(FxClaimBonusPayload.TYPE, FxClaimBonusPayload.STREAM_CODEC, FxTradingService::onClaimBonus);
        registrar.playToServer(FxShopBuyPayload.TYPE, FxShopBuyPayload.STREAM_CODEC, FxTradingService::onShopBuy);
        registrar.playToServer(FxAutoRenewPayload.TYPE, FxAutoRenewPayload.STREAM_CODEC, FxTradingService::onAutoRenew);
        registrar.playToServer(FxLoungeClaimPayload.TYPE, FxLoungeClaimPayload.STREAM_CODEC, FxTradingService::onLoungeClaim);

        registrar.playToClient(FxCurrenciesPayload.TYPE, FxCurrenciesPayload.STREAM_CODEC);
        registrar.playToClient(FxLoungePoolPayload.TYPE, FxLoungePoolPayload.STREAM_CODEC);
        registrar.playToClient(FxMarketPayload.TYPE, FxMarketPayload.STREAM_CODEC);
        registrar.playToClient(FxHistoryPayload.TYPE, FxHistoryPayload.STREAM_CODEC);
        registrar.playToClient(FxAccountPayload.TYPE, FxAccountPayload.STREAM_CODEC);
        registrar.playToClient(FxNoticePayload.TYPE, FxNoticePayload.STREAM_CODEC);
        registrar.playToClient(FxEconomyPayload.TYPE, FxEconomyPayload.STREAM_CODEC);
        registrar.playToClient(FxEventsPayload.TYPE, FxEventsPayload.STREAM_CODEC);
    }
}
