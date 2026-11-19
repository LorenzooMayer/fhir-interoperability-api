
package br.edu.ufcspa.interop.ambulatorio.fhir.provider;

import br.edu.ufcspa.interop.ambulatorio.dominio.Alergia;
import br.edu.ufcspa.interop.ambulatorio.dominio.Paciente;
import br.edu.ufcspa.interop.ambulatorio.fhir.mapper.AllergyIntoleranceMapper;
import br.edu.ufcspa.interop.ambulatorio.servico.AlergiaServico;
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
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.stereotype.Component;

@Component
public class AllergyIntoleranceResourceProvider implements IResourceProvider {

    private final AlergiaServico alergiaServico;
    private final PacienteServico pacienteServico;
    private final AllergyIntoleranceMapper mapper;

    public AllergyIntoleranceResourceProvider(
            AlergiaServico alergiaServico,
            PacienteServico pacienteServico,
            AllergyIntoleranceMapper mapper
    ) {
        this.alergiaServico = alergiaServico;
        this.pacienteServico = pacienteServico;
        this.mapper = mapper;
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return AllergyIntolerance.class;
    }

    // GET /fhir/AllergyIntolerance/{id}
    @Read
    public AllergyIntolerance buscar(@IdParam IdType id) {
        Long codigo = extrairId(id);

        Alergia alergia = alergiaServico.buscarPorId(codigo)
                .orElseThrow(() -> new ResourceNotFoundException(id));

        return mapper.paraFhir(alergia);
    }

    // POST /fhir/AllergyIntolerance
    @Create
    public MethodOutcome criar(
            @ResourceParam AllergyIntolerance recurso
    ) {
        Alergia alergia = new Alergia();

        mapper.paraDominio(recurso, alergia);
        alergia.setPaciente(resolverPaciente(recurso));

        Alergia salva = alergiaServico.salvar(alergia);

        MethodOutcome resultado = new MethodOutcome();
        resultado.setId(new IdType(
                "AllergyIntolerance", salva.getId().toString()
        ));
        resultado.setCreated(true);
        resultado.setResource(mapper.paraFhir(salva));

        return resultado;
    }

    // PUT /fhir/AllergyIntolerance/{id}
    @Update
    public MethodOutcome atualizar(
            @IdParam IdType id,
            @ResourceParam AllergyIntolerance recurso
    ) {
        Long codigo = extrairId(id);

        Alergia alergia = alergiaServico.buscarPorId(codigo)
                .orElseThrow(() -> new ResourceNotFoundException(id));

        if (recurso.hasId()
                && !codigo.toString().equals(
                recurso.getIdElement().getIdPart()
        )) {
            throw new UnprocessableEntityException(
                    "O id do recurso deve corresponder ao id da URL."
            );
        }

        mapper.paraDominio(recurso, alergia);
        alergia.setPaciente(resolverPaciente(recurso));

        Alergia salva = alergiaServico.salvar(alergia);

        MethodOutcome resultado = new MethodOutcome();
        resultado.setId(new IdType(
                "AllergyIntolerance", salva.getId().toString()
        ));
        resultado.setCreated(false);
        resultado.setResource(mapper.paraFhir(salva));

        return resultado;
    }

    // Verifica se Patient/{id} existe
    private Paciente resolverPaciente(AllergyIntolerance recurso) {
        if (!recurso.hasPatient()
                || !recurso.getPatient().hasReference()) {
            throw new UnprocessableEntityException(
                    "AllergyIntolerance deve possuir patient.reference."
            );
        }

        String referencia = recurso.getPatient().getReference();

        if (!referencia.matches("Patient/[0-9]+")) {
            throw new UnprocessableEntityException(
                    "Referencia de paciente invalida. Use Patient/{id}."
            );
        }

        Long pacienteId;

        try {
            pacienteId = Long.parseLong(
                    referencia.substring("Patient/".length())
            );
        } catch (NumberFormatException e) {
            throw new UnprocessableEntityException(
                    "Identificador do paciente invalido."
            );
        }

        return pacienteServico.buscarPorId(pacienteId)
                .orElseThrow(() -> new UnprocessableEntityException(
                        "Paciente referenciado nao existe: " + referencia
                ));
    }

    private Long extrairId(IdType id) {
        try {
            return Long.parseLong(id.getIdPart());
        } catch (NumberFormatException | NullPointerException e) {
            throw new UnprocessableEntityException(
                    "Identificador de AllergyIntolerance invalido."
            );
        }
    }
}
