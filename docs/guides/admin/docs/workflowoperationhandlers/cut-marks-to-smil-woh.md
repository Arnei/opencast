CutMarksToSmilWorkflowOperationHandler
===================================

Description
-----------

This operation parses a JSON containing cut marks into a SMIL that can be used by the 
[VideoEditorWorkflowOperation](editor-woh.md). It does this by attributing the given times to the tracks in the 
given presentation and presenter flavors. 

## Parameter Table

|configuration keys         |example                |description                                                    |
|------------------         |-----------            |---------------------------------------------------------------|
|source-presenter-flavor    |`presenter/prepared`   |The flavor of the presenter video track. Must contain exactly one file.                   |
|source-presentation-flavor |`presentation/prepared`|The flavor of the presentation video track. Must contain exactly one file.                  |
|source-json-flavor         |`smil/times`           |The flavor of the JSON. Must contain exactly one file.|
|target-smil-flavor         |`smil/cutmarks`        |The flavor of the resulting SMIL.|

## JSON Format
*begin* marks the start of a segment, *duration* its duration. Times are in milliseconds.

    [
      {
        "begin": 1672,
        "duration": 7199
      }
    ]

## Operation Example

        <operation
            id="cut-marks-to-smil"
            description="Process ingested cutmarks by applying them to current tracks"
            fail-on-error="true"
            exception-handler-workflow="partial-error">
          <configurations>
            <configuration key="source-presenter-flavor">presenter/prepared</configuration>
            <configuration key="source-presentation-flavor">presentation/prepared</configuration>
            <configuration key="source-json-flavor">smil/times</configuration>
            <configuration key="target-smil-flavor">smil/cutmarks</configuration>
          </configurations>
        </operation>

        <operation
            id="clone"
            exception-handler-workflow="partial-error">
          <configurations>
            <configuration key="source-flavor">smil/cutmarks</configuration>
            <configuration key="target-flavor">smil/cutting</configuration>
          </configurations>
        </operation>

        <operation
            id="editor"
            fail-on-error="true"
            exception-handler-workflow="error"
            description="Waiting for user to review / video edit recording">
          <configurations>
            <configuration key="source-flavors">*/prepared</configuration>
            <configuration key="skipped-flavors">*/prepared</configuration>
            <configuration key="smil-flavors">smil/cutting</configuration>
            <configuration key="target-smil-flavor">smil/cutting</configuration>
            <configuration key="target-flavor-subtype">trimmed</configuration>
          </configurations>
        </operation>