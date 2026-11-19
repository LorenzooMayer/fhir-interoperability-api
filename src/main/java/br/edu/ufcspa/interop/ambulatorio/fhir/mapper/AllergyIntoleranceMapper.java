
package br.edu.ufcspa.interop.ambulatorio.fhir.mapper;

import br.edu.ufcspa.interop.ambulatorio.dominio.*;
import br.edu.ufcspa.interop.ambulatorio.fhir.util.ConversorDatas;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

@Component
public class AllergyIntoleranceMapper {

    private static final String SISTEMA_CLINICO =
            "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical";

    private static final String SISTEMA_VERIFICACAO =
            "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification";

    // Banco de dados -> FHIR
    public AllergyIntolerance paraFhir(Alergia alergia) {
        AllergyIntolerance recurso = new AllergyIntolerance();

        if (alergia.getId() != null) {
            recurso.setId(alergia.getId().toString());
        }

        if (alergia.getPaciente() != null
                && alergia.getPaciente().getId() != null) {
            recurso.setPatient(new Reference(
                    "Patient/" + alergia.getPaciente().getId()
            ));
        }

        if (temTexto(alergia.getSubstancia())) {
            recurso.setCode(new CodeableConcept()
                    .setText(alergia.getSubstancia()));
        }

        if (alergia.getTipo() != null) {
            recurso.setType(
                    alergia.getTipo() == TipoAlergia.ALERGIA
                            ? AllergyIntolerance.AllergyIntoleranceType.ALLERGY
                            : AllergyIntolerance.AllergyIntoleranceType.INTOLERANCE
            );
        }

        if (alergia.getCategoria() != null) {
            recurso.addCategory(switch (alergia.getCategoria()) {
                case ALIMENTO ->
                        AllergyIntolerance.AllergyIntoleranceCategory.FOOD;
                case MEDICAMENTO ->
                        AllergyIntolerance.AllergyIntoleranceCategory.MEDICATION;
                case AMBIENTE ->
                        AllergyIntolerance.AllergyIntoleranceCategory.ENVIRONMENT;
                case BIOLOGICO ->
                        AllergyIntolerance.AllergyIntoleranceCategory.BIOLOGIC;
            });
        }

        if (alergia.getCriticidade() != null) {
            recurso.setCriticality(switch (alergia.getCriticidade()) {
                case BAIXA ->
                        AllergyIntolerance.AllergyIntoleranceCriticality.LOW;
                case ALTA ->
                        AllergyIntolerance.AllergyIntoleranceCriticality.HIGH;
                case INDETERMINADA ->
                        AllergyIntolerance.AllergyIntoleranceCriticality.UNABLETOASSESS;
            });
        }

        if (alergia.getSituacaoClinica() != null) {
            String codigo = switch (alergia.getSituacaoClinica()) {
                case ATIVA -> "active";
                case INATIVA -> "inactive";
                case RESOLVIDA -> "resolved";
            };

            recurso.setClinicalStatus(
                    conceito(SISTEMA_CLINICO, codigo)
            );
        }

        if (alergia.getVerificacao() != null) {
            String codigo = switch (alergia.getVerificacao()) {
                case NAO_CONFIRMADA -> "unconfirmed";
                case CONFIRMADA -> "confirmed";
                case REFUTADA -> "refuted";
                case REGISTRADA_POR_ENGANO -> "entered-in-error";
            };

            recurso.setVerificationStatus(
                    conceito(SISTEMA_VERIFICACAO, codigo)
            );
        }

        if (temTexto(alergia.getReacao())) {
            AllergyIntolerance.AllergyIntoleranceReactionComponent reacao =
                    new AllergyIntolerance.AllergyIntoleranceReactionComponent();

            reacao.addManifestation(
                    new CodeableConcept().setText(alergia.getReacao())
            );

            recurso.addReaction(reacao);
        }

        if (alergia.getDataRegistro() != null) {
            recurso.setRecordedDateElement(
                    ConversorDatas.paraDateTimeType(
                            alergia.getDataRegistro()
                    )
            );
        }

        return recurso;
    }

    // FHIR -> Banco de dados
    public void paraDominio(
            AllergyIntolerance recurso,
            Alergia destino
    ) {
        if (!recurso.hasVerificationStatus()) {
            throw erro("AllergyIntolerance deve possuir verificationStatus.");
        }

        String verificacao = codigo(
                recurso.getVerificationStatus(),
                SISTEMA_VERIFICACAO
        );

        VerificacaoAlergia verificacaoDominio =
                switch (verificacao) {
                    case "unconfirmed" ->
                            VerificacaoAlergia.NAO_CONFIRMADA;
                    case "confirmed" ->
                            VerificacaoAlergia.CONFIRMADA;
                    case "refuted" ->
                            VerificacaoAlergia.REFUTADA;
                    case "entered-in-error" ->
                            VerificacaoAlergia.REGISTRADA_POR_ENGANO;
                    default -> throw erro(
                            "verificationStatus invalido: " + verificacao
                    );
                };

        // Invariantes ait-1 e ait-2
        boolean registradoPorEngano =
                verificacaoDominio ==
                        VerificacaoAlergia.REGISTRADA_POR_ENGANO;

        if (registradoPorEngano && recurso.hasClinicalStatus()) {
            throw erro(
                    "ait-1: clinicalStatus deve estar ausente " +
                            "quando verificationStatus for entered-in-error."
            );
        }

        if (!registradoPorEngano && !recurso.hasClinicalStatus()) {
            throw erro(
                    "ait-2: clinicalStatus e obrigatorio " +
                            "quando verificationStatus nao for entered-in-error."
            );
        }

        if (!recurso.hasCode()
                || !temTexto(recurso.getCode().getText())) {
            throw erro(
                    "AllergyIntolerance deve possuir substancia em code.text."
            );
        }

        if (!recurso.hasRecordedDate()) {
            throw erro(
                    "AllergyIntolerance deve possuir recordedDate."
            );
        }

        destino.setVerificacao(verificacaoDominio);
        destino.setSubstancia(recurso.getCode().getText());

        if (recurso.hasClinicalStatus()) {
            String clinico = codigo(
                    recurso.getClinicalStatus(),
                    SISTEMA_CLINICO
            );

            destino.setSituacaoClinica(switch (clinico) {
                case "active" -> SituacaoClinicaAlergia.ATIVA;
                case "inactive" -> SituacaoClinicaAlergia.INATIVA;
                case "resolved" -> SituacaoClinicaAlergia.RESOLVIDA;
                default -> throw erro(
                        "clinicalStatus invalido: " + clinico
                );
            });
        } else {
            destino.setSituacaoClinica(null);
        }

        if (recurso.hasType()) {
            destino.setTipo(switch (recurso.getType()) {
                case ALLERGY -> TipoAlergia.ALERGIA;
                case INTOLERANCE -> TipoAlergia.INTOLERANCIA;
                default -> throw erro("type invalido.");
            });
        } else {
            destino.setTipo(null);
        }


        if (recurso.hasCategory()) {
            if (recurso.getCategory().size() != 1) {
                throw erro(
                        "O sistema permite apenas uma categoria de alergia."
                );
            }

            String categoria = recurso.getCategory().get(0).getValue().toCode();

            destino.setCategoria(switch (categoria) {
                case "food" -> CategoriaAlergia.ALIMENTO;
                case "medication" -> CategoriaAlergia.MEDICAMENTO;
                case "environment" -> CategoriaAlergia.AMBIENTE;
                case "biologic" -> CategoriaAlergia.BIOLOGICO;
                default -> throw erro("Categoria FHIR invalida: " + categoria);
            });
        } else {
            destino.setCategoria(null);
        }


        if (recurso.hasCriticality()) {
            destino.setCriticidade(
                    switch (recurso.getCriticality()) {
                        case LOW -> CriticidadeAlergia.BAIXA;
                        case HIGH -> CriticidadeAlergia.ALTA;
                        case UNABLETOASSESS ->
                                CriticidadeAlergia.INDETERMINADA;
                        default -> throw erro("criticality invalida.");
                    }
            );
        } else {
            destino.setCriticidade(null);
        }

        if (recurso.hasReaction()) {
            if (recurso.getReaction().size() != 1
                    || recurso.getReactionFirstRep()
                    .getManifestation().size() != 1) {
                throw erro(
                        "O sistema permite apenas uma reacao " +
                                "com uma manifestacao."
                );
            }

            String textoReacao = recurso.getReactionFirstRep()
                    .getManifestationFirstRep().getText();

            if (!temTexto(textoReacao)) {
                throw erro(
                        "A manifestacao da reacao deve possuir texto."
                );
            }

            destino.setReacao(textoReacao);
        } else {
            destino.setReacao(null);
        }

        destino.setDataRegistro(
                ConversorDatas.paraLocalDate(
                        recurso.getRecordedDateElement()
                )
        );
    }

    private CodeableConcept conceito(
            String sistema,
            String codigo
    ) {
        return new CodeableConcept().addCoding(
                new Coding()
                        .setSystem(sistema)
                        .setCode(codigo)
        );
    }

    private String codigo(
            CodeableConcept conceito,
            String sistema
    ) {
        return conceito.getCoding().stream()
                .filter(c -> sistema.equals(c.getSystem()))
                .map(Coding::getCode)
                .filter(this::temTexto)
                .findFirst()
                .orElseThrow(() -> erro(
                        "Codigo obrigatorio ausente para o sistema: " + sistema
                ));
    }

    private boolean temTexto(String texto) {
        return texto != null && !texto.isBlank();
    }

    private UnprocessableEntityException erro(String mensagem) {
        return new UnprocessableEntityException(mensagem);
    }
}
