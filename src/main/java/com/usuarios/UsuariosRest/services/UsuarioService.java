package com.usuarios.UsuariosRest.services;

import com.usuarios.UsuariosRest.models.UsuarioModel;
import com.usuarios.UsuariosRest.repositories.IUsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.ArrayList;
import java.util.Optional;

@Service
public class UsuarioService {
    @Autowired
    IUsuarioRepository userRepository;

    public ArrayList<UsuarioModel> getUsuario(){
        return (ArrayList<UsuarioModel>)  userRepository.findAll();
    }


    public UsuarioModel saveUser(UsuarioModel user){
        return userRepository.save(user);
    }

    public Optional<UsuarioModel> getbyId(Long id){
        return userRepository.findById(id);
    }



    public UsuarioModel updateById(UsuarioModel request , Long id) {
        Optional<UsuarioModel> optionalUser = userRepository.findById(id);

        if (optionalUser.isPresent()) {
            UsuarioModel user = optionalUser.get();
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
            user.setEmail(request.getEmail());

            // Guardar los cambios en la base de datos
            userRepository.save(user);

            return user;
        } else {
            // Manejar el caso en que el usuario no se encuentra
            throw new UserNotFoundException("El usuario que intenta eliminar no existe con el id " + id);
        }
    }



    public void deleteUser(Long id) {
        Optional<UsuarioModel> optionalUser = userRepository.findById(id);

        if (optionalUser.isPresent()) {
            try {
                userRepository.deleteById(id);
            } catch (Exception e) {
                // Manejar cualquier excepción lanzada durante la eliminación
                throw new RuntimeException("El usuario que intenta eliminar no existe con el id " + id);
            }
        } else {
            // Si el usuario no existe, lanzar una excepción personalizada
            throw new UserNotFoundException("El usuario que intenta eliminar no existe con el id " + id);
        }
    }




    public class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }






}
