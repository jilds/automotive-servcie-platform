package com.asp.platform.workshop.domain;

import java.util.List;

/// The built-in templates, in the language of the workshop's country (PT: pt-PT, BR: pt-BR, otherwise English).
public final class DefaultChecklists {

    private DefaultChecklists() {
    }

    /// @param country ISO 3166-1 alpha-2 code of the workshop
    public static ChecklistTemplate of(ChecklistKind kind, String country) {
        List<TemplateItem> items = switch (country) {
            case "PT" -> portugal(kind);
            case "BR" -> brazil(kind);
            default -> english(kind);
        };
        return new ChecklistTemplate(kind, items, false, 0);
    }

    private static List<TemplateItem> portugal(ChecklistKind kind) {
        return switch (kind) {
            case CHECK_IN -> List.of(
                    new TemplateItem("Documentos do veículo recebidos", true),
                    new TemplateItem("Chaves recebidas", true),
                    new TemplateItem("Nível de combustível e quilómetros confirmados", true),
                    new TemplateItem("Danos na carroçaria registados e fotografados", true),
                    new TemplateItem("Objetos de valor retirados do veículo", true),
                    new TemplateItem("Roda sobresselente e ferramentas verificadas", false));
            case DELIVERY -> List.of(
                    new TemplateItem("Trabalho conferido com a queixa do cliente", true),
                    new TemplateItem("Teste de condução efetuado", true),
                    new TemplateItem("Trabalho e valores explicados ao cliente", true),
                    new TemplateItem("Chaves e documentos devolvidos", true),
                    new TemplateItem("Veículo limpo", false),
                    new TemplateItem("Peças substituídas entregues, se pedido", false));
        };
    }

    private static List<TemplateItem> brazil(ChecklistKind kind) {
        return switch (kind) {
            case CHECK_IN -> List.of(
                    new TemplateItem("Documentos do veículo recebidos", true),
                    new TemplateItem("Chaves recebidas", true),
                    new TemplateItem("Nível de combustível e quilometragem confirmados", true),
                    new TemplateItem("Avarias na lataria registradas e fotografadas", true),
                    new TemplateItem("Objetos de valor retirados do veículo", true),
                    new TemplateItem("Estepe e ferramentas verificados", false));
            case DELIVERY -> List.of(
                    new TemplateItem("Serviço conferido com a reclamação do cliente", true),
                    new TemplateItem("Test drive realizado", true),
                    new TemplateItem("Serviço e valores explicados ao cliente", true),
                    new TemplateItem("Chaves e documentos devolvidos", true),
                    new TemplateItem("Veículo limpo", false),
                    new TemplateItem("Peças substituídas entregues, se solicitado", false));
        };
    }

    private static List<TemplateItem> english(ChecklistKind kind) {
        return switch (kind) {
            case CHECK_IN -> List.of(
                    new TemplateItem("Vehicle documents received", true),
                    new TemplateItem("Keys received", true),
                    new TemplateItem("Fuel level and odometer confirmed", true),
                    new TemplateItem("Bodywork damage noted and photographed", true),
                    new TemplateItem("Valuables removed from the vehicle", true),
                    new TemplateItem("Spare wheel and tools checked", false));
            case DELIVERY -> List.of(
                    new TemplateItem("Work checked against the customer's complaint", true),
                    new TemplateItem("Test drive done", true),
                    new TemplateItem("Work and costs explained to the customer", true),
                    new TemplateItem("Keys and documents returned", true),
                    new TemplateItem("Vehicle clean", false),
                    new TemplateItem("Replaced parts handed back if asked", false));
        };
    }
}
