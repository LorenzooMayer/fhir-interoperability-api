
package br.edu.ufcspa.interop.ambulatorio.fhir.provider;

import br.edu.ufcspa.interop.ambulatorio.dominio.Paciente;
import br.edu.ufcspa.interop.ambulatorio.dominio.SinalVital;
import br.edu.ufcspa.interop.ambulatorio.fhir.mapper.ObservationMapper;
import br.edu.ufcspa.interop.ambulatorio.servico.PacienteServico;
import br.edu.ufcspa.interop.ambulatorio.servico.SinalVitalServico;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;

import org.springframework.stereotype.Component;

@Component
public class ObservationResourceProvider implements IResourceProvider {

    private final SinalVitalServico servico;
    private final PacienteServico pacienteServico;
    private final ObservationMapper mapper;

    public ObservationResourceProvider(
            SinalVitalServico servico,
            PacienteServico pacienteServico,
            ObservationMapper mapper) {

        this.servico = servico;
        this.pacienteServico = pacienteServico;
        this.mapper = mapper;
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return Observation.class;
    }

    // GET /fhir/Observation/{id}
    @Read
    public Observation read(@IdParam IdType id) {

        Long sinalVitalId = converterId(id);

        SinalVital sinalVital = servico.buscarPorId(sinalVitalId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(id)
                );

        return mapper.paraFhir(sinalVital);
    }

    // POST /fhir/Observation
    @Create
    public MethodOutcome create(@ResourceParam Observation recurso) {

        SinalVital sinalVital = new SinalVital();

        // Valida os dados da Observation e converte para o dominio.
        mapper.paraDominio(recurso, sinalVital);

        // Verifica se o paciente referenciado existe.
        Paciente paciente = buscarPaciente(recurso.getSubject());
        sinalVital.setPaciente(paciente);

        // Salva o novo sinal vital.
        SinalVital salvo = servico.salvar(sinalVital);

        MethodOutcome resultado = new MethodOutcome();

        resultado.setId(
                new IdType("Observation", salvo.getId().toString())
        );

        resultado.setCreated(true);
        resultado.setResource(mapper.paraFhir(salvo));

        return resultado;
    }

    // PUT /fhir/Observation/{id}
    @Update
    public MethodOutcome update(
            @IdParam IdType id,
            @ResourceParam Observation recurso) {

        Long sinalVitalId = converterId(id);

        // O recurso precisa existir para ser atualizado.
        SinalVital sinalVital = servico.buscarPorId(sinalVitalId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(id)
                );

        // Verifica se o ID do JSON corresponde ao ID da URL.
        if (recurso.hasId()
                && !sinalVitalId.toString().equals(
                recurso.getIdElement().getIdPart())) {

            throw new UnprocessableEntityException(
                    "O id do recurso deve corresponder ao id da URL."
            );
        }

        // Substitui os campos da medicao pelos valores recebidos.
        mapper.paraDominio(recurso, sinalVital);

        // A referencia ao paciente tambem pode ser atualizada.
        Paciente paciente = buscarPaciente(recurso.getSubject());
        sinalVital.setPaciente(paciente);

        // Persiste a atualizacao.
        SinalVital atualizado = servico.salvar(sinalVital);

        MethodOutcome resultado = new MethodOutcome();

        resultado.setId(
                new IdType("Observation", atualizado.getId().toString())
        );

        resultado.setCreated(false);
        resultado.setResource(mapper.paraFhir(atualizado));

        return resultado;
    }

    // Valida a referencia Patient/{id} e consulta o banco.
    private Paciente buscarPaciente(Reference referencia) {

        if (referencia == null || !referencia.hasReference()) {
            throw new UnprocessableEntityException(
                    "Observation deve possuir subject com referencia ao paciente."
            );
        }

        String texto = referencia.getReference();

        if (!texto.matches("Patient/[0-9]+")) {
            throw new UnprocessableEntityException(
                    "Referencia invalida. Use Patient/{id}."
            );
        }

        String idTexto = texto.substring("Patient/".length());

        Long pacienteId;

        try {
            pacienteId = Long.valueOf(idTexto);
        } catch (NumberFormatException e) {
            throw new UnprocessableEntityException(
                    "ID do paciente invalido."
            );
        }

        return pacienteServico.buscarPorId(pacienteId)
                .orElseThrow(() ->
                        new UnprocessableEntityException(
                                "Paciente referenciado nao existe: " + texto
                        )
                );
    }

    // Converte o identificador FHIR para Long.
    private Long converterId(IdType id) {

        try {
            if (id == null || id.getIdPart() == null) {
                throw new NumberFormatException("ID ausente");
            }

            return Long.valueOf(id.getIdPart());

        } catch (NumberFormatException e) {
            throw new UnprocessableEntityException(
                    "O ID da Observation deve ser numerico."
            );
        }
    }
}
