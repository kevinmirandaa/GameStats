# Estandares de codigo

Aplican a todo agente y a todo lenguaje del proyecto.

## Reglas obligatorias

1. El codigo debe leerse como prosa. El nombre explica la intencion, por largo que sea.
2. Cero comentarios en el codigo. Si algo necesita explicacion, se renombra o se extrae a una funcion con buen nombre.
3. Cero emojis.
4. Cero lineas decorativas o separadores hechos con guiones, signos igual o asteriscos.
5. Cada funcion hace una sola cosa y cabe en una pantalla corta.
6. Sin numeros ni textos magicos: se nombran como constantes.
7. Sin codigo duplicado: se extrae.
8. Los errores se manejan de forma explicita. Nada de ignorar errores.
9. Las dependencias entre paquetes van en una sola direccion. Los detalles de HTTP y almacenamiento no entran al dominio.
10. Cada comportamiento tiene prueba automatizada. Las pruebas tambien se escriben con nombres que describen el caso.

## Go

- Solo libreria estandar salvo justificacion escrita en `docs/DECISIONS.md`.
- Paquetes pequenos por responsabilidad dentro de `internal/`.
- Interfaces pequenas definidas donde se consumen.
- Concurrencia con goroutines y channels. Cada goroutine tiene un dueno claro y una forma de terminar.
- `go vet` y `gofmt` limpios.
- Pruebas con `go test -race`.

## Scala

- Scala 3. Estilo funcional: `case class` y `enum` inmutables, `val`, sin `var`, sin `null`, sin excepciones para control de flujo (usar `Either` u `Option`).
- Funciones puras en el dominio. Efectos solo en la capa de borde.
- Preferir `map`, `flatMap`, `filter`, `collect`, `groupBy`, `foldLeft`, `reduce`, `sliding` frente a bucles.
- Un archivo por concepto principal.
- Pruebas con munit.

## Documentos Markdown

- Sin emojis. Sin lineas decorativas. Listas y encabezados simples.
