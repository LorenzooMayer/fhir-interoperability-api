
package br.edu.ufcspa.interop.ambulatorio.fhir.provider;

import br.edu.ufcspa.interop.ambulatorio.dominio.Paciente;
import br.edu.ufcspa.interop.ambulatorio.fhir.mapper.PatientMapper;
import br.edu.ufcspa.interop.ambulatorio.servico.PacienteServico;

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
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class PatientResourceProvider implements IResourceProvider {

    private final PacienteServico servico;
    private final PatientMapper mapper;

    public PatientResourceProvider(
            PacienteServico servico,
            PatientMapper mapper) {
        this.servico = servico;
        this.mapper = mapper;
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return Patient.class;
    }

    @Read
    public Patient read(@IdParam IdType id) {
        Long pacienteId = converterId(id);

        Paciente paciente = servico.buscarPorId(pacienteId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(id));

        return mapper.paraFhir(paciente);
    }

    @Create
    public MethodOutcome create(@ResourceParam Patient recurso) {
        Paciente paciente = new Paciente();

        mapper.paraDominio(recurso, paciente);

        Paciente salvo = servico.salvar(paciente);

        MethodOutcome resultado = new MethodOutcome();
        resultado.setId(new IdType("Patient", salvo.getId().toString()));
        resultado.setCreated(true);
        resultado.setResource(mapper.paraFhir(salvo));

        return resultado;
    }

    @Update
    public MethodOutcome update(
            @IdParam IdType id,
            @ResourceParam Patient recurso) {

        Long pacienteId = converterId(id);

        Paciente paciente = servico.buscarPorId(pacienteId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(id));

        mapper.paraDominio(recurso, paciente);

        Paciente atualizado = servico.salvar(paciente);

        MethodOutcome resultado = new MethodOutcome();
        resultado.setId(new IdType("Patient", atualizado.getId().toString()));
        resultado.setCreated(false);
        resultado.setResource(mapper.paraFhir(atualizado));

        return resultado;
    }

    private Long converterId(IdType id) {
        try {
            return Long.valueOf(id.getIdPart());
        } catch (NumberFormatException e) {
            throw new UnprocessableEntityException(
                    "O ID do paciente deve ser numerico."
            );
        }
    }
}
