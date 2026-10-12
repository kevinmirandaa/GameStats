# Guia de Git para GameStats

Repositorio: https://github.com/kevinmirandaa/GameStats

## Conceptos

- Repositorio: la carpeta del proyecto con todo su historial.
- Commit: un punto de guardado con un mensaje.
- Rama: una linea de trabajo paralela. `main` siempre debe estar estable.
- Pull Request (PR): una peticion en GitHub para unir una rama a `main` despues de revisarla.
- Remoto (`origin`): la copia en GitHub.

## Comandos

- `git status` muestra que archivos cambiaron y cuales estan preparados. Usalo antes y despues de cada paso.
- `git add .` prepara todos los cambios para el siguiente commit. `git add ruta/archivo` prepara solo uno.
- `git commit -m "feat: mensaje"` guarda lo preparado.
- `git switch -c feature/fase-01-diseno` crea una rama nueva y se mueve a ella.
- `git switch main` vuelve a la rama principal.
- `git push -u origin feature/fase-01-diseno` sube la rama a GitHub la primera vez. Despues basta `git push`.
- `git pull` baja los cambios de GitHub a la rama actual.
- `git log --oneline` muestra el historial resumido.
- `git diff` muestra los cambios aun no preparados.

## Mensajes de commit

Formato `tipo: descripcion corta en minusculas`.

- `feat` funcionalidad nueva.
- `fix` correccion.
- `docs` documentacion.
- `test` pruebas.
- `build` Docker, dependencias, compilacion.
- `ci` pipelines.
- `refactor` reorganizar sin cambiar el comportamiento.

Ejemplos de este proyecto:

- `docs: add project blueprint and agents`
- `feat: validate event envelope in ingest-go`
- `test: cover comeback pattern in racing analyzer`
- `build: add multi stage dockerfile for analytics-scala`

## Ciclo de cada fase

1. `git switch main` y `git pull`.
2. `git switch -c feature/fase-NN-nombre`.
3. Ejecutar el agente de la fase.
4. `git status` para revisar lo que cambio.
5. `git add .` y `git commit -m "..."`. Puede ser uno o varios commits.
6. `git push -u origin feature/fase-NN-nombre`.
7. Abrir el Pull Request en GitHub hacia `main`.
8. Ejecutar la compuerta QA. Si aprueba, unir el PR en GitHub con Merge.
9. `git switch main` y `git pull` para traer lo unido.

## Si algo sale mal

- Cambios hechos por error en un archivo, sin preparar: `git restore ruta/archivo`.
- Quitar un archivo de lo preparado: `git restore --staged ruta/archivo`.
- Corregir el mensaje del ultimo commit si aun no se subio: `git commit --amend -m "nuevo mensaje"`.
- Ante cualquier duda, copiar la salida de `git status` y pedir ayuda antes de seguir.
