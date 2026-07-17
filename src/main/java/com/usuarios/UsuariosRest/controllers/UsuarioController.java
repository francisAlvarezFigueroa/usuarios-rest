package com.usuarios.UsuariosRest.controllers;


import com.usuarios.UsuariosRest.models.UsuarioModel;
import com.usuarios.UsuariosRest.services.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Optional;

@RestController
@RequestMapping("/user")
public class UsuarioController {
    @Autowired
    private UsuarioService userService;





    @GetMapping
    public ArrayList<UsuarioModel> getUsers(){
        return this.userService.getUsuario();
    }



    @GetMapping(path = "/{id}")
    public ResponseEntity<?> getUserById(@PathVariable("id") Long id){
        Optional<UsuarioModel> user = this.userService.getbyId(id);
        if (user.isPresent()) {
            return ResponseEntity.ok(user.get());
        } else {
            return ResponseEntity.badRequest().body("no se encontró el usuario que busca con el id " + id);
        }
    }



    @PostMapping
    public UsuarioModel saveUser(@RequestBody UsuarioModel user){
        return  this.userService.saveUser(user);
    }



    @PutMapping(path = "/{id}")
    public ResponseEntity<?> updateUserById(@RequestBody UsuarioModel request, @PathVariable("id") Long id) {
        try {
            UsuarioModel updatedUser = this.userService.updateById(request, id);
            if (updatedUser != null) {
                return ResponseEntity.ok(updatedUser);
            } else {
                return ResponseEntity.badRequest().body("El usuario con el id no existe: " + id);
            }
        } catch (UsuarioService.UserNotFoundException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }



    @DeleteMapping(path = "/{id}")
    public ResponseEntity<?> deleteUserById(@PathVariable("id") Long id){
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok().build();
        } catch (UsuarioService.UserNotFoundException e) {
            return ResponseEntity.badRequest().body("User con id " + id + " no existe");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


}
