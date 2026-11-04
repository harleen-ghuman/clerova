package com.clerova.trucking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What the app gets for loads. Amounts are in cents. */
public final class LoadResponses {

    private LoadResponses() {
    }

    public record BrokerInfo(
            UUID id, String name, String mcNumber, String email, String phone,
            FmcsaStatus fmcsaStatus, String fmcsaName, Instant fmcsaCheckedAt
    ) {
        static BrokerInfo of(Broker broker) {
            return broker == null ? null : new BrokerInfo(
                    broker.getId(), broker.getName(), broker.getMcNumber(), broker.getEmail(), broker.getPhone(),
                    broker.getFmcsaStatus(), broker.getFmcsaName(), broker.getFmcsaCheckedAt());
        }
    }

    public record StopInfo(StopKind kind, String place, String appointment) {
    }

    public record FlagInfo(
            UUID id, String code, FlagLevel level, String title, String detail, String sourceText, Instant resolvedAt
    ) {
    }

    public record LoadSummary(
            UUID id, String loadNumber, LoadStatus status, String brokerName,
            String origin, String destination, Long rateCents, int openFlags, Instant createdAt
    ) {
    }

    public record LoadDetail(
            UUID id, String loadNumber, LoadStatus status, BrokerInfo broker, String equipment,
            Long rateCents, Long agreedRateCents, Integer detentionFreeMinutes, Long detentionRateCents,
            String detentionRule, Long tonuCents, String paymentTerms, String paperworkRule, String rateConFile,
            List<StopInfo> stops, List<FlagInfo> flags, Instant createdAt, Instant updatedAt
    ) {
    }

    static LoadSummary summary(Load load) {
        List<LoadStop> stops = load.getStops();

        return new LoadSummary(
                load.getId(),
                load.getLoadNumber(),
                load.getStatus(),
                load.getBroker() != null ? load.getBroker().getName() : null,
                stops.isEmpty() ? null : stops.getFirst().getPlace(),
                stops.size() < 2 ? null : stops.getLast().getPlace(),
                load.getRateCents(),
                (int) load.getFlags().stream().filter(flag -> flag.getResolvedAt() == null).count(),
                load.getCreatedAt()
        );
    }

    static LoadDetail detail(Load load) {
        return new LoadDetail(
                load.getId(), load.getLoadNumber(), load.getStatus(), BrokerInfo.of(load.getBroker()),
                load.getEquipment(), load.getRateCents(), load.getAgreedRateCents(),
                load.getDetentionFreeMinutes(), load.getDetentionRateCents(), load.getDetentionRule(),
                load.getTonuCents(), load.getPaymentTerms(), load.getPaperworkRule(), load.getRateConFile(),
                load.getStops().stream().map(s -> new StopInfo(s.getKind(), s.getPlace(), s.getAppointment())).toList(),
                load.getFlags().stream().map(f -> new FlagInfo(
                        f.getId(), f.getCode(), f.getLevel(), f.getTitle(), f.getDetail(), f.getSourceText(),
                        f.getResolvedAt())).toList(),
                load.getCreatedAt(), load.getUpdatedAt()
        );
    }
}
