
package br.edu.ufcspa.interop.ambulatorio.fhir.mapper;

import br.edu.ufcspa.interop.ambulatorio.dominio.*;
import br.edu.ufcspa.interop.ambulatorio.fhir.util.Codigos;
import br.edu.ufcspa.interop.ambulatorio.fhir.util.ConversorDatas;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class ObservationMapper {

    private static final String LOINC = "http://loinc.org";
    private static final String UCUM = "http://unitsofmeasure.org";

    private static final String CATEGORIA =
            "http://terminology.hl7.org/CodeSystem/observation-category";

    public Observation paraFhir(SinalVital sinalVital) {
        Observation recurso = new Observation();

        if (sinalVital.getId() != null) {
            recurso.setId(sinalVital.getId().toString());
        }

        if (sinalVital.getPaciente() != null
                && sinalVital.getPaciente().getId() != null) {
            recurso.setSubject(new Reference(
                    "Patient/" + sinalVital.getPaciente().getId()
            ));
        }

        if (sinalVital.getSituacao() != null) {
            recurso.setStatus(paraStatusFhir(sinalVital.getSituacao()));
        }

        recurso.addCategory(
                Codigos.conceito(
                        CATEGORIA,
                        "vital-signs",
                        "Vital Signs"
                )
        );

        if (sinalVital.getTipo() != null) {
            TipoSinalVital tipo = sinalVital.getTipo();

            recurso.setCode(
                    Codigos.conceito(
                            LOINC,
                            codigoLoinc(tipo),
                            tipo.getDescricao()
                    )
            );

            if (sinalVital.getValor() != null) {
                Quantity quantidade = new Quantity();

                quantidade.setValue(
                        BigDecimal.valueOf(sinalVital.getValor())
                );

                quantidade.setSystem(UCUM);
                quantidade.setCode(codigoUcum(tipo));
                quantidade.setUnit(tipo.getUnidadeLocal());

                recurso.setValue(quantidade);
            }
        }

        if (sinalVital.getDataHoraMedicao() != null) {
            recurso.setEffective(
                    ConversorDatas.paraDateTimeType(
                            sinalVital.getDataHoraMedicao()
                    )
            );
        }

        return recurso;
    }

    public void paraDominio(Observation recurso, SinalVital destino) {

        if (!recurso.hasStatus()) {
            throw new UnprocessableEntityException(
                    "Observation deve possuir status."
            );
        }

        SituacaoSinalVital situacao =
                paraSituacaoDominio(recurso.getStatus());

        String codigo = Codigos.codigo(
                recurso.getCode(), LOINC
        ).orElseThrow(() ->
                new UnprocessableEntityException(
                        "Observation deve possuir codigo LOINC."
                )
        );

        TipoSinalVital tipo = tipoPorLoinc(codigo);

        if (!recurso.hasValueQuantity()) {
            throw new UnprocessableEntityException(
                    "Observation deve possuir valueQuantity."
            );
        }

        Quantity quantidade = recurso.getValueQuantity();

        if (!quantidade.hasValue()) {
            throw new UnprocessableEntityException(
                    "Observation deve possuir valor numerico."
            );
        }

        if (!UCUM.equals(quantidade.getSystem())) {
            throw new UnprocessableEntityException(
                    "Sistema da unidade deve ser UCUM."
            );
        }

        String unidadeEsperada = codigoUcum(tipo);

        if (!unidadeEsperada.equals(quantidade.getCode())) {
            throw new UnprocessableEntityException(
                    "Unidade UCUM incompatível com o tipo de sinal vital. "
                            + "Esperado: " + unidadeEsperada
            );
        }

        if (!recurso.hasEffectiveDateTimeType()) {
            throw new UnprocessableEntityException(
                    "Observation deve possuir effectiveDateTime."
            );
        }

        LocalDateTime dataHora;

        try {
            dataHora = ConversorDatas.paraLocalDateTime(
                    recurso.getEffectiveDateTimeType()
            );
        } catch (IllegalArgumentException e) {
            throw new UnprocessableEntityException(
                    "Data e hora da medicao invalidas."
            );
        }

        destino.setTipo(tipo);
        destino.setSituacao(situacao);
        destino.setValor(quantidade.getValue().doubleValue());
        destino.setUnidade(tipo.getUnidadeLocal());
        destino.setDataHoraMedicao(dataHora);
    }

    private String codigoLoinc(TipoSinalVital tipo) {
        return switch (tipo) {
            case FREQUENCIA_CARDIACA -> "8867-4";
            case FREQUENCIA_RESPIRATORIA -> "9279-1";
            case TEMPERATURA -> "8310-5";
            case PESO -> "29463-7";
            case ALTURA -> "8302-2";
            case SATURACAO_O2 -> "59408-5";
        };
    }

    private String codigoUcum(TipoSinalVital tipo) {
        return switch (tipo) {
            case FREQUENCIA_CARDIACA -> "/min";
            case FREQUENCIA_RESPIRATORIA -> "/min";
            case TEMPERATURA -> "Cel";
            case PESO -> "kg";
            case ALTURA -> "cm";
            case SATURACAO_O2 -> "%";
        };
    }

    private TipoSinalVital tipoPorLoinc(String codigo) {
        return switch (codigo) {
            case "8867-4" -> TipoSinalVital.FREQUENCIA_CARDIACA;
            case "9279-1" -> TipoSinalVital.FREQUENCIA_RESPIRATORIA;
            case "8310-5" -> TipoSinalVital.TEMPERATURA;
            case "29463-7" -> TipoSinalVital.PESO;
            case "8302-2" -> TipoSinalVital.ALTURA;
            case "59408-5" -> TipoSinalVital.SATURACAO_O2;
            default -> throw new UnprocessableEntityException(
                    "Codigo LOINC nao permitido: " + codigo
            );
        };
    }

    private Observation.ObservationStatus paraStatusFhir(
            SituacaoSinalVital situacao) {

        return switch (situacao) {
            case PRELIMINAR ->
                    Observation.ObservationStatus.PRELIMINARY;
            case FINAL ->
                    Observation.ObservationStatus.FINAL;
            case CORRIGIDO ->
                    Observation.ObservationStatus.CORRECTED;
            case CANCELADO ->
                    Observation.ObservationStatus.CANCELLED;
            case REGISTRADO_POR_ENGANO ->
                    Observation.ObservationStatus.ENTEREDINERROR;
        };
    }

    private SituacaoSinalVital paraSituacaoDominio(
            Observation.ObservationStatus status) {

        return switch (status) {
            case PRELIMINARY -> SituacaoSinalVital.PRELIMINAR;
            case FINAL -> SituacaoSinalVital.FINAL;
            case CORRECTED -> SituacaoSinalVital.CORRIGIDO;
            case CANCELLED -> SituacaoSinalVital.CANCELADO;
            case ENTEREDINERROR ->
                    SituacaoSinalVital.REGISTRADO_POR_ENGANO;
            default -> throw new UnprocessableEntityException(
                    "Status de Observation nao suportado: " + status
            );
        };
    }
}
