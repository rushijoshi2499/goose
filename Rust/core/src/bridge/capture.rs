use super::{BridgeRequest, BridgeResponse, bridge_error};

pub(crate) fn dispatch_capture(request: &BridgeRequest) -> BridgeResponse {
    bridge_error(
        &request.request_id,
        "not_implemented",
        format!("capture dispatch not yet implemented: {}", request.method),
    )
}
