package com.usuarios.UsuariosRest.repositories;

import com.usuarios.UsuariosRest.models.UsuarioModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IUsuarioRepository extends JpaRepository<UsuarioModel,Long> {

}
