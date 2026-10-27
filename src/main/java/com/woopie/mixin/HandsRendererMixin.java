package com.woopie.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.woopie.SwordRenderer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HandsRendererMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void woopie$submitArmWithItem(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks,
                                          float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight,
                                          PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
        if (hand != InteractionHand.MAIN_HAND) { var mp = net.minecraft.client.Minecraft.getInstance().player; if (mp != null && mp.getMainHandItem().is(ItemTags.SWORDS)) ci.cancel(); return; }
        if (itemStack.isEmpty() || !itemStack.is(ItemTags.SWORDS)) {
            SwordRenderer.notHolding();
            return;
        }
        var mcp = net.minecraft.client.Minecraft.getInstance().player;
        if (mcp != null && !mcp.getMainHandItem().is(ItemTags.SWORDS)) {
            // Player already switched away from the sword: hide it immediately instead of waiting for vanilla's lowering.
            SwordRenderer.notHolding();
            ci.cancel();
            return;
        }
        var avatar = playerState.avatarRenderState;
        if (avatar == null) return;
        SwordRenderer.render(poseStack, collector, avatar.skin, avatar.swingAnimation, lightCoords, avatar.isCrouching, inverseArmHeight);
        ci.cancel();
    }
}
