
package br.edu.ufcspa.interop.ambulatorio.fhir.mapper;

import br.edu.ufcspa.interop.ambulatorio.dominio.Paciente;
import br.edu.ufcspa.interop.ambulatorio.dominio.Sexo;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class PatientMapper {

    public static final String SYSTEM_CPF =
            "http://rnds.saude.gov.br/fhir/r4/NamingSystem/cpf";

    public static final String SYSTEM_CNS =
            "http://rnds.saude.gov.br/fhir/r4/NamingSystem/cns";

    public Patient paraFhir(Paciente paciente) {
        Patient recurso = new Patient();

        if (paciente.getId() != null) {
            recurso.setId(paciente.getId().toString());
        }

        recurso.setActive(paciente.isAtivo());

        if (temTexto(paciente.getCpf())) {
            recurso.addIdentifier()
                    .setUse(Identifier.IdentifierUse.OFFICIAL)
                    .setSystem(SYSTEM_CPF)
                    .setValue(paciente.getCpf());
        }

        if (temTexto(paciente.getCns())) {
            recurso.addIdentifier()
                    .setSystem(SYSTEM_CNS)
                    .setValue(paciente.getCns());
        }

        if (temTexto(paciente.getNome())
                || temTexto(paciente.getSobrenome())) {

            HumanName nome = recurso.addName();
            nome.setUse(HumanName.NameUse.OFFICIAL);

            if (temTexto(paciente.getSobrenome())) {
                nome.setFamily(paciente.getSobrenome());
            }

            if (temTexto(paciente.getNome())) {
                for (String parte : paciente.getNome().trim().split("\\s+")) {
                    nome.addGiven(parte);
                }
            }
        }

        if (temTexto(paciente.getTelefone())) {
            recurso.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.PHONE)
                    .setValue(paciente.getTelefone());
        }

        if (temTexto(paciente.getEmail())) {
            recurso.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.EMAIL)
                    .setValue(paciente.getEmail());
        }

        if (paciente.getDataNascimento() != null) {
            recurso.setBirthDateElement(
                    new DateType(paciente.getDataNascimento().toString())
            );
        }

        if (paciente.getSexo() != null) {
            recurso.setGender(switch (paciente.getSexo()) {
                case MASCULINO -> Enumerations.AdministrativeGender.MALE;
                case FEMININO -> Enumerations.AdministrativeGender.FEMALE;
                case OUTRO -> Enumerations.AdministrativeGender.OTHER;
                case NAO_INFORMADO -> Enumerations.AdministrativeGender.UNKNOWN;
            });
        }

        return recurso;
    }

    public void paraDominio(Patient recurso, Paciente destino) {

        String cpf = null;
        String cns = null;

        for (Identifier identificador : recurso.getIdentifier()) {
            if (SYSTEM_CPF.equals(identificador.getSystem())) {
                cpf = identificador.getValue();
            } else if (SYSTEM_CNS.equals(identificador.getSystem())) {
                cns = identificador.getValue();
            }
        }

        if (cpf == null || !cpf.matches("[0-9]{11}")) {
            throw new UnprocessableEntityException(
                    "CPF obrigatorio e deve conter exatamente 11 digitos."
            );
        }

        if (cns != null && !cns.matches("[0-9]{15}")) {
            throw new UnprocessableEntityException(
                    "CNS deve conter exatamente 15 digitos."
            );
        }

        String nome = null;
        String sobrenome = null;

        for (HumanName nomeFhir : recurso.getName()) {
            if (!temTexto(nomeFhir.getFamily())) {
                continue;
            }

            StringBuilder partesNome = new StringBuilder();

            for (StringType given : nomeFhir.getGiven()) {
                if (temTexto(given.getValue())) {
                    if (partesNome.length() > 0) {
                        partesNome.append(" ");
                    }
                    partesNome.append(given.getValue().trim());
                }
            }

            if (partesNome.length() > 0) {
                nome = partesNome.toString();
                sobrenome = nomeFhir.getFamily().trim();
                break;
            }
        }

        if (!temTexto(nome) || !temTexto(sobrenome)) {
            throw new UnprocessableEntityException(
                    "Nome e sobrenome sao obrigatorios."
            );
        }

        String telefone = null;
        String email = null;

        for (ContactPoint contato : recurso.getTelecom()) {
            if (!temTexto(contato.getValue())) {
                continue;
            }

            if (contato.getSystem() ==
                    ContactPoint.ContactPointSystem.PHONE) {
                telefone = contato.getValue();
            } else if (contato.getSystem() ==
                    ContactPoint.ContactPointSystem.EMAIL) {
                email = contato.getValue();
            }
        }

        Sexo sexo = null;

        if (recurso.hasGender()) {
            sexo = switch (recurso.getGender()) {
                case MALE -> Sexo.MASCULINO;
                case FEMALE -> Sexo.FEMININO;
                case OTHER -> Sexo.OUTRO;
                case UNKNOWN -> Sexo.NAO_INFORMADO;
                default -> null;
            };
        }

        LocalDate nascimento = null;


        if (recurso.hasBirthDate()) {
            String data = recurso.getBirthDateElement().getValueAsString();

            if (!data.matches("\\d{4}-\\d{2}-\\d{2}")) {
                throw new UnprocessableEntityException(
                        "birthDate deve conter uma data completa no formato AAAA-MM-DD."
                );
            }

            try {
                nascimento = LocalDate.parse(data);
            } catch (java.time.format.DateTimeParseException e) {
                throw new UnprocessableEntityException(
                        "birthDate contem uma data invalida."
                );
            }
        }


        // Atribuicoes somente apos as validacoes obrigatorias.
        destino.setCpf(cpf);
        destino.setCns(cns);
        destino.setNome(nome);
        destino.setSobrenome(sobrenome);
        destino.setTelefone(telefone);
        destino.setEmail(email);
        destino.setSexo(sexo);
        destino.setDataNascimento(nascimento);

        // Se active estiver ausente, usamos o valor padrao do dominio.
        destino.setAtivo(!recurso.hasActive() || recurso.getActive());
    }

    private boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
